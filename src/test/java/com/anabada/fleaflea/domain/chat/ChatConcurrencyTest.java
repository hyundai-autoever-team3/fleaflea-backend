package com.anabada.fleaflea.domain.chat;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomResponse;
import com.anabada.fleaflea.domain.chat.exception.ChatFriendRequiredException;
import com.anabada.fleaflea.domain.chat.repository.ChatMessageRepository;
import com.anabada.fleaflea.domain.chat.repository.ChatRoomRepository;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.friendship.service.FriendshipService;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.fixture.ChatFixture;
import com.anabada.fleaflea.fixture.FriendshipFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@Sql(
        statements = "TRUNCATE TABLE chat_messages, chat_rooms, friendships, members RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        statements = "TRUNCATE TABLE chat_messages, chat_rooms, friendships, members RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class ChatConcurrencyTest {

    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    @Autowired
    private ChatService chatService;

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private Member sender;
    private Member receiver;
    private Friendship friendship;

    @BeforeEach
    void setUp() {
        sender = memberRepository.save(MemberFixture.createMember("sender"));
        receiver = memberRepository.save(MemberFixture.createMember("receiver"));
        friendship = friendshipRepository.save(
                FriendshipFixture.createFriendship(receiver, sender, FriendshipStatus.ACCEPTED)
        );
    }

    @Test
    @DisplayName("동시에 채팅을 시작해도 방은 하나만 생성한다")
    void getOrCreateChatRoom_concurrentRequests_createOneRoom() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Long> firstRequest = executor.submit(() -> {
                awaitStart(ready, start);

                return chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
            });
            Future<Long> secondRequest = executor.submit(() -> {
                awaitStart(ready, start);

                return chatService.getOrCreateChatRoom(receiver.getMemberId(), sender.getMemberId()).id();
            });

            assertThat(ready.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(firstRequest.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(secondRequest.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertThat(chatRoomRepository.count()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("같은 메시지를 동시에 재전송해도 하나만 저장한다")
    void sendMessage_concurrentRetries_persistOneMessage() throws Exception {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageSendRequest chatMessageSendRequest = createChatMessageSendRequest("firstRequest message");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<ChatMessageResponse> firstRequest = executor.submit(() -> {
                awaitStart(ready, start);

                return chatService.sendMessage(sender.getMemberId(), roomId, chatMessageSendRequest);
            });
            Future<ChatMessageResponse> secondRequest = executor.submit(() -> {
                awaitStart(ready, start);

                return chatService.sendMessage(sender.getMemberId(), roomId, chatMessageSendRequest);
            });

            assertThat(ready.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(firstRequest.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(secondRequest.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertThat(chatMessageRepository.count()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("메시지 전송과 읽음 처리가 동시에 발생해도 읽음 상태를 보존한다")
    void sendMessage_concurrentRead_preservesReadState() throws Exception {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageResponse first = sendMessage(sender, roomId, "first");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<ChatMessageResponse> sending = executor.submit(() -> {
                awaitStart(ready, start);

                return sendMessage(sender, roomId, "second");
            });
            Future<ChatReadResponse> reading = executor.submit(() -> {
                awaitStart(ready, start);

                return chatService.markMessagesAsRead(receiver.getMemberId(), roomId, first.id());
            });

            assertThat(ready.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            ChatMessageResponse second = sending.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            reading.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ChatRoomResponse room = chatService.getChatRoom(receiver.getMemberId(), roomId);

            assertThat(room.myLastReadId()).isEqualTo(first.id());
            assertThat(room.lastMessageId()).isEqualTo(second.id());
            assertThat(room.unreadCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("친구 삭제와 전송이 겹쳐도 삭제 이후 전송은 차단하고 커밋된 메시지만 보존한다")
    void sendMessage_concurrentFriendDeletion_preservesCommittedHistory() throws Exception {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> sending = executor.submit(() -> {
                awaitStart(ready, start);

                try {
                    sendMessage(sender, roomId, "삭제와 동시에 전송");
                    return true;
                } catch (ChatFriendRequiredException exception) {
                    return false;
                }
            });
            Future<?> deleting = executor.submit(() -> {
                awaitStart(ready, start);
                friendshipService.deleteFriend(sender.getMemberId(), friendship.getFriendshipId());

                return null;
            });

            assertThat(ready.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean messageCommitted = sending.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            deleting.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(chatMessageRepository.count()).isEqualTo(messageCommitted ? 1L : 0L);
            assertThat(chatService.getChatRoom(sender.getMemberId(), roomId).canSend()).isFalse();
            assertThat(chatService.getMessages(receiver.getMemberId(), roomId, null, null, 30).content())
                    .hasSize(messageCommitted ? 1 : 0);
            assertThatThrownBy(() -> sendMessage(sender, roomId, "삭제 후 전송"))
                    .isInstanceOf(ChatFriendRequiredException.class);
        }
    }

    private ChatMessageSendRequest createChatMessageSendRequest(String content) {
        return ChatFixture.createChatMessageSendRequest(content);
    }

    private ChatMessageResponse sendMessage(
            Member member,
            Long roomId,
            String content
    ) {
        return chatService.sendMessage(member.getMemberId(), roomId, createChatMessageSendRequest(content));
    }

    private void awaitStart(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException  {
        ready.countDown();

        if (!start.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 신호를 받지 못했습니다.");
        }
    }
}
