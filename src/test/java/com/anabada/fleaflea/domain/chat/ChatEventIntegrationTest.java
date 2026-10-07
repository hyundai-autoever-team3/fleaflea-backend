package com.anabada.fleaflea.domain.chat;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.repository.ChatMessageRepository;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.chat.dto.ChatSocketEventResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@PostgresIntegrationTest
@Sql(
        statements = "TRUNCATE TABLE chat_messages, chat_rooms, friendships, members RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        statements = "TRUNCATE TABLE chat_messages, chat_rooms, friendships, members RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class ChatEventIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Member sender;
    private Member receiver;
    private Member outsider;

    @BeforeEach
    void setUp() {
        sender = memberRepository.save(MemberFixture.createMember("sender"));
        receiver = memberRepository.save(MemberFixture.createMember("receiver"));
        outsider = memberRepository.save(MemberFixture.createMember("outsider"));
        friendshipRepository.save(
                FriendshipFixture.createFriendship(receiver, sender, FriendshipStatus.ACCEPTED)
        );
    }

    @Test
    @DisplayName("메시지 저장 트랜잭션을 롤백하면 메시지와 WebSocket 이벤트가 남지 않는다")
    void sendMessage_rollback_doesNotPersistOrPublish() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sendMessage(sender, roomId, "rolled back");
            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), eq("/queue/chat"), any());
            status.setRollbackOnly();
        });

        assertThat(chatMessageRepository.count()).isZero();
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), eq("/queue/chat"), any());
    }

    @Test
    @DisplayName("메시지 저장이 커밋되면 두 참여자에게만 WebSocket 이벤트를 전달한다")
    void sendMessage_commit_publishesOnlyToParticipants() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();

        ChatMessageResponse saved = sendMessage(sender, roomId, "committed");

        verify(messagingTemplate, timeout(3000)).convertAndSendToUser(
                        sender.getMemberId().toString(),
                        "/queue/chat",
                        new ChatSocketEventResponse("chat-message", saved)
                );
        verify(messagingTemplate, timeout(3000)).convertAndSendToUser(
                        receiver.getMemberId().toString(),
                        "/queue/chat",
                        new ChatSocketEventResponse("chat-message", saved)
                );
        verify(messagingTemplate, never()).convertAndSendToUser(eq(outsider.getMemberId().toString()), anyString(), any());
    }

    @Test
    @DisplayName("메시지 재전송은 시각과 ID를 유지하고 참여자별 WebSocket를 한 번만 전달한다")
    void sendMessage_retryPreservesTimestampAndPublishesOnce() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageSendRequest request = createSendRequest("재전송");

        ChatMessageResponse original = chatService.sendMessage(sender.getMemberId(), roomId, request);
        ChatMessageResponse retry = chatService.sendMessage(sender.getMemberId(), roomId, request);

        assertThat(retry).isEqualTo(original);
        assertThat(chatMessageRepository.findById(original.id()).orElseThrow().getCreatedAt())
                .isEqualTo(original.createdAt());
        assertThat(chatMessageRepository.count()).isEqualTo(1);
        verify(messagingTemplate, timeout(3000).times(1))
                .convertAndSendToUser(
                        sender.getMemberId().toString(),
                        "/queue/chat",
                        new ChatSocketEventResponse("chat-message", original)
                );
        verify(messagingTemplate, timeout(3000).times(1))
                .convertAndSendToUser(
                        receiver.getMemberId().toString(),
                        "/queue/chat",
                        new ChatSocketEventResponse("chat-message", original)
                );
    }

    @Test
    @DisplayName("읽음 위치 변경이 커밋되면 두 참여자에게 읽음 상태를 전달한다")
    void markMessagesAsRead_publishesCommittedReadReceipt() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageResponse message = sendMessage(sender, roomId, "읽음 확인");

        ChatReadResponse response = chatService.markMessagesAsRead(receiver.getMemberId(), roomId, message.id());

        verify(messagingTemplate, timeout(3000))
                .convertAndSendToUser(
                        sender.getMemberId().toString(),
                        "/queue/chat",
                        new ChatSocketEventResponse("chat-read", response)
                );
        verify(messagingTemplate, timeout(3000))
                .convertAndSendToUser(
                        receiver.getMemberId().toString(),
                        "/queue/chat",
                        new ChatSocketEventResponse("chat-read", response)
                );
        assertThat(response.lastReadMessageId()).isEqualTo(message.id());
    }

    private ChatMessageSendRequest createSendRequest(String content) {
        return ChatFixture.createSendRequest(content);
    }

    private ChatMessageResponse sendMessage(Member member, Long roomId, String content) {
        return chatService.sendMessage(member.getMemberId(), roomId, createSendRequest(content));
    }
}
