package com.anabada.fleaflea.domain.chat.service;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatTypingResponse;
import com.anabada.fleaflea.domain.chat.event.ChatEvent;
import com.anabada.fleaflea.domain.chat.exception.ChatDuplicateMessageConflictException;
import com.anabada.fleaflea.domain.chat.exception.ChatFriendRequiredException;
import com.anabada.fleaflea.domain.chat.exception.ChatMessageNotFoundException;
import com.anabada.fleaflea.domain.chat.exception.ChatNotParticipantException;
import com.anabada.fleaflea.domain.chat.exception.ChatRateLimitExceededException;
import com.anabada.fleaflea.domain.chat.exception.ChatRoomNotFoundException;
import com.anabada.fleaflea.domain.chat.exception.InvalidChatMessageCursorException;
import com.anabada.fleaflea.domain.chat.repository.ChatMessageRepository;
import com.anabada.fleaflea.domain.chat.repository.ChatRoomRepository;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.fixture.ChatFixture;
import com.anabada.fleaflea.fixture.FriendshipFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long FRIEND_ID = 2L;
    private static final Long ROOM_ID = 10L;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChatService chatService;

    private Member sender;
    private Member receiver;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        sender = MemberFixture.createMember(MEMBER_ID);
        receiver = MemberFixture.createMember(FRIEND_ID);
        chatRoom = ChatFixture.createChatRoomWithId(ROOM_ID, MEMBER_ID, FRIEND_ID);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("친구인 참여자의 입력 시작과 종료는 저장 없이 이벤트로 전달한다")
    void updateTypingStatus_publishesEventWithoutSaving(boolean typing) {
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(chatRoom));
        when(friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                MEMBER_ID, FRIEND_ID, FriendshipStatus.ACCEPTED
        )).thenReturn(true);

        chatService.updateTypingStatus(MEMBER_ID, ROOM_ID, typing);

        verify(eventPublisher).publishEvent(new ChatEvent(
                MEMBER_ID, FRIEND_ID, ChatEvent.TYPING_CHANGED,
                ChatTypingResponse.from(chatRoom, MEMBER_ID, typing)
        ));
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verifyNoInteractions(chatMessageRepository, memberRepository, imageService);
    }

    @Test
    @DisplayName("친구가 아닌 참여자는 입력 상태를 전송할 수 없다")
    void updateTypingStatus_rejectsFormerFriend() {
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> chatService.updateTypingStatus(MEMBER_ID, ROOM_ID, true))
                .isInstanceOf(ChatFriendRequiredException.class);

        verifyNoInteractions(eventPublisher, chatMessageRepository);
    }

    @Test
    @DisplayName("제삼자는 입력 상태를 전송할 수 없다")
    void updateTypingStatus_rejectsNonParticipant() {
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> chatService.updateTypingStatus(3L, ROOM_ID, true))
                .isInstanceOf(ChatNotParticipantException.class);

        verifyNoInteractions(friendshipRepository, eventPublisher, chatMessageRepository);
    }

    @Test
    @DisplayName("없는 채팅방에는 입력 상태를 전송할 수 없다")
    void updateTypingStatus_rejectsMissingRoom() {
        assertThatThrownBy(() -> chatService.updateTypingStatus(MEMBER_ID, ROOM_ID, true))
                .isInstanceOf(ChatRoomNotFoundException.class);

        verifyNoInteractions(friendshipRepository, eventPublisher, chatMessageRepository);
    }

    @Test
    @DisplayName("자기 자신과 채팅을 시작하면 친구 관계 예외가 발생한다")
    void getOrCreateChatRoom_rejectsSelf() {
        assertThatThrownBy(() -> chatService.getOrCreateChatRoom(MEMBER_ID, MEMBER_ID))
                .isInstanceOf(ChatFriendRequiredException.class);

        verifyNoInteractions(chatRoomRepository, memberRepository);
    }

    @Test
    @DisplayName("존재하지 않는 회원은 채팅을 시작할 수 없다")
    void getOrCreateChatRoom_rejectsMissingMember() {
        when(memberRepository.findLockedById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getOrCreateChatRoom(MEMBER_ID, FRIEND_ID))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 채팅방은 채팅방 전용 예외로 처리한다")
    void getChatRoom_rejectsMissingRoom() {
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getChatRoom(MEMBER_ID, ROOM_ID))
                .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    @DisplayName("채팅방 참여자가 아닌 회원은 상세 정보를 조회할 수 없다")
    void getChatRoom_rejectsNonParticipant() {
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> chatService.getChatRoom(3L, ROOM_ID))
                .isInstanceOf(ChatNotParticipantException.class);

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("수락된 친구 관계가 없으면 메시지를 저장하지 않는다")
    void sendMessage_rejectsNonFriend() {
        when(memberRepository.findLockedById(MEMBER_ID)).thenReturn(Optional.of(sender));
        when(chatRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> chatService.sendMessage(MEMBER_ID, ROOM_ID, ChatFixture.createChatMessageSendRequest("안녕")))
                .isInstanceOf(ChatFriendRequiredException.class);

        verifyNoInteractions(chatMessageRepository, eventPublisher);
    }

    @Test
    @DisplayName("같은 메시지를 재전송하면 기존 응답을 반환하고 저장과 이벤트 발행을 반복하지 않는다")
    void sendMessage_reusesExistingMessageWithoutPublishingEvent() {
        ChatMessageSendRequest chatMessageSendRequest = ChatFixture.createChatMessageSendRequest("안녕");
        ChatMessage existingMessage = ChatFixture.createChatMessageWithId(
                20L, ROOM_ID, MEMBER_ID, chatMessageSendRequest, LocalDateTime.of(2026, 1, 1, 12, 0)
        );
        prepareMessageSender();
        when(chatMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(
                ROOM_ID, MEMBER_ID, chatMessageSendRequest.clientMessageId().toString()
        )).thenReturn(Optional.of(existingMessage));

        ChatMessageResponse response = chatService.sendMessage(MEMBER_ID, ROOM_ID, chatMessageSendRequest);

        assertThat(response).isEqualTo(ChatMessageResponse.from(existingMessage));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("같은 클라이언트 메시지 ID에 다른 내용이 전달되면 중복 충돌 예외가 발생한다")
    void sendMessage_rejectsChangedRetryContent() {
        ChatMessageSendRequest originalRequest = ChatFixture.createChatMessageSendRequest("원래 내용");
        ChatMessage existingMessage = ChatFixture.createChatMessageWithId(
                20L, ROOM_ID, MEMBER_ID, originalRequest, LocalDateTime.of(2026, 1, 1, 12, 0)
        );
        prepareMessageSender();
        when(chatMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(
                ROOM_ID, MEMBER_ID, originalRequest.clientMessageId().toString()
        )).thenReturn(Optional.of(existingMessage));
        ChatMessageSendRequest changedRequest = ChatFixture.createChatMessageSendRequest("다른 내용", originalRequest.clientMessageId());

        assertThatThrownBy(() -> chatService.sendMessage(MEMBER_ID, ROOM_ID, changedRequest))
                .isInstanceOf(ChatDuplicateMessageConflictException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("최근 1분간 60개를 전송한 회원은 추가 메시지를 저장할 수 없다")
    void sendMessage_rejectsRateLimit() {
        prepareMessageSender();
        when(chatMessageRepository.countRecentMessagesBySenderId(eq(MEMBER_ID), any(LocalDateTime.class)))
                .thenReturn(60L);

        assertThatThrownBy(() -> chatService.sendMessage(MEMBER_ID, ROOM_ID, ChatFixture.createChatMessageSendRequest("초과")))
                .isInstanceOfSatisfying(ChatRateLimitExceededException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMessageCursors")
    @DisplayName("잘못된 커서 조합이나 조회 범위는 채팅 커서 전용 예외로 처리한다")
    void getMessages_rejectsInvalidCursor(
            String caseName,
            Long beforeId,
            Long afterId,
            int size
    ) {
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> chatService.getMessages(MEMBER_ID, ROOM_ID, beforeId, afterId, size))
                .isInstanceOfSatisfying(InvalidChatMessageCursorException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("방에서 메시지를 찾을 수 없으면 메시지 전용 예외로 처리한다")
    void markMessagesAsRead_rejectsMissingMessage() {
        when(chatRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(chatRoom));
        when(chatMessageRepository.findByIdAndRoomId(20L, ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.markMessagesAsRead(MEMBER_ID, ROOM_ID, 20L))
                .isInstanceOf(ChatMessageNotFoundException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이미 읽은 메시지를 다시 읽음 처리하면 이벤트를 발행하지 않는다")
    void markMessagesAsRead_doesNotPublishUnchangedWatermark() {
        chatRoom.markMessagesAsRead(MEMBER_ID, 20L);
        ChatMessage message = ChatFixture.createChatMessage(ROOM_ID, FRIEND_ID, "읽은 메시지");
        when(chatRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(chatRoom));
        when(chatMessageRepository.findByIdAndRoomId(20L, ROOM_ID)).thenReturn(Optional.of(message));

        chatService.markMessagesAsRead(MEMBER_ID, ROOM_ID, 20L);

        assertThat(chatRoom.getLastReadMessageId(MEMBER_ID)).isEqualTo(20L);
        verifyNoInteractions(eventPublisher);
    }

    private static Stream<Arguments> invalidMessageCursors() {
        return Stream.of(
                Arguments.of("두 커서 동시 지정", 1L, 0L, 30),
                Arguments.of("이전 커서 0", 0L, null, 30),
                Arguments.of("이전 커서 음수", -1L, null, 30),
                Arguments.of("복구 커서 음수", null, -1L, 30),
                Arguments.of("크기 0", null, null, 0),
                Arguments.of("크기 음수", null, null, -1),
                Arguments.of("크기 최대값 초과", null, null, 101)
        );
    }

    private void prepareMessageSender() {
        when(memberRepository.findLockedById(MEMBER_ID)).thenReturn(Optional.of(sender));
        when(chatRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(chatRoom));
        when(friendshipRepository.lockRelationship(MEMBER_ID, FRIEND_ID, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(FriendshipFixture.createFriendship(sender, receiver, FriendshipStatus.ACCEPTED)));
    }
}
