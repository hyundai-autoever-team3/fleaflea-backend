package com.anabada.fleaflea.domain.chat;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomListResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.exception.ChatDuplicateMessageConflictException;
import com.anabada.fleaflea.domain.chat.exception.ChatFriendRequiredException;
import com.anabada.fleaflea.domain.chat.exception.ChatMessageNotFoundException;
import com.anabada.fleaflea.domain.chat.exception.ChatNotParticipantException;
import com.anabada.fleaflea.domain.chat.exception.ChatRateLimitExceededException;
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
import com.anabada.fleaflea.global.dto.CursorPageResponse;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@Transactional
class ChatServiceIntegrationTest {

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
    private Member outsider;
    private Friendship friendship;

    @BeforeEach
    void setUp() {
        sender = memberRepository.save(MemberFixture.createMember("sender"));
        receiver = memberRepository.save(MemberFixture.createMember("receiver"));
        outsider = memberRepository.save(MemberFixture.createMember("outsider"));
        friendship = friendshipRepository.save(
                FriendshipFixture.createFriendship(receiver, sender, FriendshipStatus.ACCEPTED)
        );
    }

    @Test
    @DisplayName("친구 간 요청 방향이 달라도 같은 채팅방을 사용한다")
    void getOrCreateChatRoom_reversedParticipants_reusesRoom() {
        ChatRoomResponse room = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId());

        ChatRoomResponse reversedRoom = chatService.getOrCreateChatRoom(receiver.getMemberId(), sender.getMemberId());

        assertThat(reversedRoom.id()).isEqualTo(room.id());
        assertThat(chatRoomRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("친구 관계가 없으면 채팅방을 생성할 수 없다")
    void getOrCreateChatRoom_withoutFriendship_rejectsRequest() {
        assertThatThrownBy(() -> chatService.getOrCreateChatRoom(sender.getMemberId(), outsider.getMemberId()))
                .isInstanceOf(ChatFriendRequiredException.class);

        assertThat(chatRoomRepository.count()).isZero();
    }

    @Test
    @DisplayName("친구 요청이 대기 중이면 채팅방을 생성할 수 없다")
    void getOrCreateChatRoom_pendingFriendship_rejectsRequest() {
        friendshipRepository.save(FriendshipFixture.createFriendship(sender, outsider, FriendshipStatus.PENDING));

        assertThatThrownBy(() -> chatService.getOrCreateChatRoom(sender.getMemberId(), outsider.getMemberId()))
                .isInstanceOf(ChatFriendRequiredException.class);

        assertThat(chatRoomRepository.count()).isZero();
    }

    @ParameterizedTest(name = "참여자 권한 검사: {0}")
    @EnumSource(ChatOperation.class)
    @DisplayName("채팅방 참여자가 아니면 조회·전송·읽음 처리를 할 수 없다")
    void chatOperations_nonParticipant_rejectsRequest(ChatOperation operation) {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageResponse message = sendMessage(sender, roomId, "hello");

        assertThatThrownBy(() -> {
            switch (operation) {
                case GET_ROOM -> chatService.getChatRoom(outsider.getMemberId(), roomId);
                case SEND_MESSAGE -> chatService.sendMessage(outsider.getMemberId(), roomId, createSendRequest("hello"));
                case GET_MESSAGES -> chatService.getMessages(outsider.getMemberId(), roomId, null, null, 30);
                case MARK_READ -> chatService.markMessagesAsRead(outsider.getMemberId(), roomId, message.id());
            }
        }).isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("친구 삭제 후에도 대화 이력을 조회하고 읽음 처리할 수 있다")
    void deleteFriend_preservesHistoryAndAllowsReading() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageResponse message = sendMessage(sender, roomId, "약속 장소");
        friendshipService.deleteFriend(sender.getMemberId(), friendship.getFriendshipId());

        ChatRoomResponse room = chatService.getChatRoom(sender.getMemberId(), roomId);
        CursorPageResponse<ChatMessageResponse> history = chatService.getMessages(receiver.getMemberId(), roomId, null, null, 30);
        ChatReadResponse readResponse = chatService.markMessagesAsRead(receiver.getMemberId(), roomId, message.id());

        assertThat(room.canSend()).isFalse();
        assertThat(history.content()).containsExactly(message);
        assertThat(readResponse.lastReadMessageId()).isEqualTo(message.id());
    }

    @Test
    @DisplayName("친구 삭제 후에는 새 메시지를 저장할 수 없다")
    void sendMessage_deletedFriendship_rejectsRequest() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        friendshipService.deleteFriend(sender.getMemberId(), friendship.getFriendshipId());

        assertThatThrownBy(() -> sendMessage(sender, roomId, "새 메시지"))
                .isInstanceOf(ChatFriendRequiredException.class);

        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 메시지를 재전송하면 저장된 응답을 반환하고 중복 저장하지 않는다")
    void sendMessage_retry_returnsPersistedMessage() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageSendRequest request = createSendRequest("hello");
        ChatMessageResponse first = chatService.sendMessage(sender.getMemberId(), roomId, request);

        ChatMessageResponse retry = chatService.sendMessage(sender.getMemberId(), roomId, request);

        assertThat(retry).isEqualTo(first);
        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 메시지 ID로 다른 내용을 보내면 기존 메시지를 변경하지 않는다")
    void sendMessage_changedRetryContent_preservesOriginalMessage() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageSendRequest originalRequest = createSendRequest("원래 내용");
        ChatMessageResponse original = chatService.sendMessage(sender.getMemberId(), roomId, originalRequest);
        ChatMessageSendRequest changedRequest = ChatFixture.createSendRequest("다른 내용", originalRequest.clientMessageId());

        assertThatThrownBy(() -> chatService.sendMessage(sender.getMemberId(), roomId, changedRequest))
                .isInstanceOf(ChatDuplicateMessageConflictException.class);

        assertThat(chatService.getMessages(sender.getMemberId(), roomId, null, null, 30).content())
                .containsExactly(original);
    }

    @Test
    @DisplayName("선택 커서가 null이면 최신 메시지부터 조회하고 이전 커서로 다음 페이지를 조회한다")
    void getMessages_nullCursors_returnsNewestPage() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageResponse first = sendMessage(sender, roomId, "1");
        ChatMessageResponse second = sendMessage(sender, roomId, "2");
        ChatMessageResponse third = sendMessage(sender, roomId, "3");

        CursorPageResponse<ChatMessageResponse> newest = chatService.getMessages(receiver.getMemberId(), roomId, null, null, 2);
        CursorPageResponse<ChatMessageResponse> older = chatService.getMessages(receiver.getMemberId(), roomId, newest.nextCursor(), null, 2);

        assertThat(newest.content()).containsExactly(third, second);
        assertThat(newest.hasNext()).isTrue();
        assertThat(newest.nextCursor()).isEqualTo(second.id());
        assertThat(older.content()).containsExactly(first);
        assertThat(older.hasNext()).isFalse();
    }

    @Test
    @DisplayName("복구 커서 0부터 오래된 메시지를 조회하고 다음 커서로 누락 이력을 이어서 조회한다")
    void getMessages_afterCursor_returnsChronologicalPages() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageResponse first = sendMessage(sender, roomId, "1");
        ChatMessageResponse second = sendMessage(sender, roomId, "2");
        ChatMessageResponse third = sendMessage(sender, roomId, "3");

        CursorPageResponse<ChatMessageResponse> firstPage = chatService.getMessages(receiver.getMemberId(), roomId, null, 0L, 2);
        CursorPageResponse<ChatMessageResponse> nextPage = chatService.getMessages(receiver.getMemberId(), roomId, null, firstPage.nextCursor(), 2);

        assertThat(firstPage.content()).containsExactly(first, second);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo(second.id());
        assertThat(nextPage.content()).containsExactly(third);
        assertThat(nextPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("과거 메시지를 읽음 처리해도 읽음 위치가 뒤로 이동하거나 안 읽은 개수가 증가하지 않는다")
    void markMessagesAsRead_olderMessage_preservesWatermarkAndUnreadCount() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        ChatMessageResponse first = sendMessage(sender, roomId, "1");
        ChatMessageResponse second = sendMessage(sender, roomId, "2");
        sendMessage(sender, roomId, "3");
        chatService.markMessagesAsRead(receiver.getMemberId(), roomId, second.id());

        chatService.markMessagesAsRead(receiver.getMemberId(), roomId, first.id());
        ChatRoomResponse room = chatService.getChatRoom(receiver.getMemberId(), roomId);
        ChatRoomListResponse rooms = chatService.getChatRooms(receiver.getMemberId(), 0, 20);

        assertThat(room.myLastReadId()).isEqualTo(second.id());
        assertThat(room.unreadCount()).isEqualTo(1);
        assertThat(rooms.rooms()).singleElement().satisfies(response -> assertThat(response.unreadCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("분당 전송 횟수를 초과하면 채팅 전용 예외로 거절한다")
    void sendMessage_exceededRateLimit_rejectsRequest() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        for (int index = 0; index < 60; index++) {
            sendMessage(sender, roomId, "message " + index);
        }

        assertThatThrownBy(() -> sendMessage(sender, roomId, "61"))
                .isInstanceOfSatisfying(ChatRateLimitExceededException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED));
        assertThat(chatMessageRepository.count()).isEqualTo(60);
    }

    @Test
    @DisplayName("다른 채팅방의 메시지로 읽음 처리를 할 수 없다")
    void markMessagesAsRead_messageFromAnotherRoom_rejectsRequest() {
        Long roomId = chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId()).id();
        friendshipRepository.save(FriendshipFixture.createFriendship(sender, outsider, FriendshipStatus.ACCEPTED));
        Long otherRoom = chatService.getOrCreateChatRoom(sender.getMemberId(), outsider.getMemberId()).id();
        ChatMessageResponse otherMessage = sendMessage(sender, otherRoom, "another room");

        assertThatThrownBy(() -> chatService.markMessagesAsRead(receiver.getMemberId(), roomId, otherMessage.id()))
                .isInstanceOf(ChatMessageNotFoundException.class);
    }

    @Test
    @DisplayName("이력이 없는 회원은 빈 채팅방 목록을 조회할 수 있다")
    void getChatRooms_returnsEmptyListForMemberWithoutRooms() {
        ChatRoomListResponse response = chatService.getChatRooms(outsider.getMemberId(), 0, 20);

        assertThat(response.rooms()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("친구 삭제 후 재요청이 대기 중이어도 기존 방의 전송 가능 여부는 false이다")
    void getChatRooms_doesNotTreatPendingRequestAsFriend() {
        chatService.getOrCreateChatRoom(sender.getMemberId(), receiver.getMemberId());
        friendshipService.deleteFriend(sender.getMemberId(), friendship.getFriendshipId());
        friendshipRepository.save(
                FriendshipFixture.createFriendship(sender, receiver, FriendshipStatus.PENDING)
        );

        assertThat(chatService.getChatRooms(sender.getMemberId(), 0, 20).rooms())
                .singleElement()
                .satisfies(room -> {
                    assertThat(room.canSend()).isFalse();
                    assertThat(room.friendId()).isEqualTo(receiver.getMemberId());
                    assertThat(room.nickname()).isEqualTo(receiver.getNickname());
                    assertThat(room.unreadCount()).isZero();
                });
    }

    private enum ChatOperation {
        GET_ROOM, SEND_MESSAGE, GET_MESSAGES, MARK_READ
    }

    private ChatMessageSendRequest createSendRequest(String content) {
        return ChatFixture.createSendRequest(content);
    }

    private ChatMessageResponse sendMessage(Member member, Long roomId, String content) {
        return chatService.sendMessage(member.getMemberId(), roomId, createSendRequest(content));
    }
}
