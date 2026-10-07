package com.anabada.fleaflea.domain.chat.service;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomListResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomResponse;
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
import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.dto.CursorPageResponse;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int MESSAGE_LIMIT_PER_MINUTE = 60;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final FriendshipRepository friendshipRepository;
    private final MemberRepository memberRepository;
    private final ImageService imageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatRoomResponse getOrCreateChatRoom(
            Long memberId,
            Long friendId
    ) {
        if (memberId.equals(friendId)) {
            throw new ChatFriendRequiredException();
        }

        long memberLowId = Math.min(memberId, friendId);
        long memberHighId = Math.max(memberId, friendId);

        // 같은 회원 쌍의 생성 요청을 직렬화한다. DB 유니크 제약도 함께 유지한다.
        memberRepository.findLockedById(memberLowId)
                .orElseThrow(MemberNotFoundException::new);

        if (!memberRepository.existsById(memberHighId)) {
            throw new MemberNotFoundException();
        }

        validateAcceptedFriendship(memberId, friendId);

        ChatRoom chatRoom = chatRoomRepository
                .findByMemberLowIdAndMemberHighId(memberLowId, memberHighId)
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.create(memberId, friendId)));

        return toChatRoomResponse(chatRoom, memberId);
    }

    public ChatRoomListResponse getChatRooms(
            Long memberId,
            int page,
            int size
    ) {
        Slice<ChatRoom> chatRooms = chatRoomRepository.findChatRoomsByMemberId(
                memberId,
                PageRequest.of(page, size)
        );

        if (chatRooms.isEmpty()) {
            return ChatRoomListResponse.from(List.of(), false);
        }

        List<Long> friendIds = chatRooms.stream()
                .map(chatRoom -> chatRoom.getOtherMemberId(memberId))
                .toList();
        List<Long> roomIds = chatRooms.stream()
                .map(ChatRoom::getId)
                .toList();

        Map<Long, Member> friendsById = new HashMap<>();
        memberRepository.findAllById(friendIds)
                .forEach(friend -> friendsById.put(friend.getMemberId(), friend));

        Set<Long> acceptedFriendIds = getAcceptedFriendIds(memberId, friendIds);
        Map<Long, Long> unreadCountsByRoomId = new HashMap<>();
        chatMessageRepository.countUnreadMessagesByRoomIds(roomIds, memberId)
                .forEach(count -> unreadCountsByRoomId.put(count.getRoomId(), count.getUnreadCount()));

        List<ChatRoomResponse> responses = chatRooms.stream()
                .map(chatRoom -> {
                    Long friendId = chatRoom.getOtherMemberId(memberId);

                    return toChatRoomResponse(
                            chatRoom,
                            memberId,
                            friendsById.get(friendId),
                            acceptedFriendIds.contains(friendId),
                            unreadCountsByRoomId.getOrDefault(chatRoom.getId(), 0L)
                    );
                })
                .toList();

        return ChatRoomListResponse.from(responses, chatRooms.hasNext());
    }

    public ChatRoomResponse getChatRoom(
            Long memberId,
            Long roomId
    ) {
        ChatRoom chatRoom = getChatRoomForParticipant(memberId, roomId);

        return toChatRoomResponse(chatRoom, memberId);
    }

    @Transactional
    public ChatMessageResponse sendMessage(
            Long memberId,
            Long roomId,
            ChatMessageSendRequest chatMessageSendRequest
    ) {
        // 회원별 전송 제한과 재전송 검증이 동시 요청에서도 동일하게 적용되도록 잠근다.
        memberRepository.findLockedById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        ChatRoom chatRoom = getChatRoomForParticipantWithLock(memberId, roomId);
        Long friendId = chatRoom.getOtherMemberId(memberId);
        validateAcceptedFriendship(memberId, friendId);

        String content = chatMessageSendRequest.content();
        String clientMessageId = chatMessageSendRequest.clientMessageId().toString();
        Optional<ChatMessage> existingMessage = chatMessageRepository
                .findByRoomIdAndSenderIdAndClientMessageId(roomId, memberId, clientMessageId);

        if (existingMessage.isPresent()) {
            ChatMessage chatMessage = existingMessage.get();

            if (!chatMessage.getContent().equals(content)) {
                throw new ChatDuplicateMessageConflictException();
            }

            return ChatMessageResponse.from(chatMessage);
        }

        long recentMessageCount = chatMessageRepository.countRecentMessagesBySenderId(
                memberId,
                LocalDateTime.now().minusMinutes(1)
        );

        if (recentMessageCount >= MESSAGE_LIMIT_PER_MINUTE) {
            throw new ChatRateLimitExceededException();
        }

        ChatMessage chatMessage = chatMessageRepository.save(
                ChatMessage.create(roomId, memberId, content, clientMessageId)
        );
        chatRoom.recordMessage(chatMessage);

        ChatMessageResponse response = ChatMessageResponse.from(chatMessage);
        eventPublisher.publishEvent(
                new ChatEvent(memberId, friendId, ChatEvent.MESSAGE_SENT, response)
        );

        return response;
    }

    public CursorPageResponse<ChatMessageResponse> getMessages(
            Long memberId,
            Long roomId,
            Long beforeId,
            Long afterId,
            int size
    ) {
        getChatRoomForParticipant(memberId, roomId);
        validateMessageCursor(beforeId, afterId, size);

        List<ChatMessage> chatMessages;
        PageRequest pageRequest = PageRequest.of(0, size + 1);

        if (afterId == null) {
            long beforeMessageId = beforeId == null ? Long.MAX_VALUE : beforeId;
            chatMessages = chatMessageRepository.findMessagesBeforeId(roomId, beforeMessageId, pageRequest);
        } else {
            chatMessages = chatMessageRepository.findMessagesAfterId(roomId, afterId, pageRequest);
        }

        boolean hasNext = chatMessages.size() > size;
        List<ChatMessageResponse> responses = chatMessages.stream()
                .limit(size)
                .map(ChatMessageResponse::from)
                .toList();
        Long nextCursor = responses.isEmpty() ? null : responses.getLast().id();

        return CursorPageResponse.from(responses, nextCursor, hasNext);
    }

    @Transactional
    public ChatReadResponse markMessagesAsRead(
            Long memberId,
            Long roomId,
            Long messageId
    ) {
        ChatRoom chatRoom = getChatRoomForParticipantWithLock(memberId, roomId);
        chatMessageRepository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(ChatMessageNotFoundException::new);

        long previousReadMessageId = chatRoom.getLastReadMessageId(memberId);
        chatRoom.markMessagesAsRead(memberId, messageId);

        ChatReadResponse response = ChatReadResponse.from(chatRoom, memberId);

        if (previousReadMessageId != response.lastReadMessageId()) {
            eventPublisher.publishEvent(
                    new ChatEvent(memberId, chatRoom.getOtherMemberId(memberId), ChatEvent.MESSAGES_READ, response)
            );
        }

        return response;
    }

    public void updateTypingStatus(
            Long memberId,
            Long roomId,
            boolean typing
    ) {
        ChatRoom chatRoom = getChatRoomForParticipant(memberId, roomId);
        Long friendId = chatRoom.getOtherMemberId(memberId);

        if (!isAcceptedFriendship(memberId, friendId)) {
            throw new ChatFriendRequiredException();
        }

        eventPublisher.publishEvent(new ChatEvent(
                memberId,
                friendId,
                ChatEvent.TYPING_CHANGED,
                ChatTypingResponse.from(chatRoom, memberId, typing)
        ));
    }

    private ChatRoom getChatRoomForParticipant(
            Long memberId,
            Long roomId
    ) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        validateParticipant(chatRoom, memberId);

        return chatRoom;
    }

    private ChatRoom getChatRoomForParticipantWithLock(
            Long memberId,
            Long roomId
    ) {
        ChatRoom chatRoom = chatRoomRepository.findLockedById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        validateParticipant(chatRoom, memberId);

        return chatRoom;
    }

    private void validateParticipant(
            ChatRoom chatRoom,
            Long memberId
    ) {
        if (!chatRoom.isParticipant(memberId)) {
            throw new ChatNotParticipantException();
        }
    }

    private void validateAcceptedFriendship(
            Long memberId,
            Long friendId
    ) {
        // 친구 삭제와 같은 관계 행을 잠가 삭제 도중 새 메시지가 저장되지 않도록 한다.
        if (friendshipRepository.lockRelationship(memberId, friendId, FriendshipStatus.ACCEPTED).isEmpty()) {
            throw new ChatFriendRequiredException();
        }
    }

    private Set<Long> getAcceptedFriendIds(
            Long memberId,
            List<Long> friendIds
    ) {
        Set<Long> acceptedFriendIds = new HashSet<>();
        List<Friendship> relationships = friendshipRepository.findActiveRelationships(memberId, friendIds);

        for (Friendship friendship : relationships) {
            if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
                continue;
            }

            Long requesterId = friendship.getRequester().getMemberId();
            Long friendId = requesterId.equals(memberId)
                    ? friendship.getAddressee().getMemberId()
                    : requesterId;
            acceptedFriendIds.add(friendId);
        }

        return acceptedFriendIds;
    }

    private boolean isAcceptedFriendship(
            Long memberId,
            Long friendId
    ) {
        return friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                memberId, friendId, FriendshipStatus.ACCEPTED
        ) || friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                friendId, memberId, FriendshipStatus.ACCEPTED
        );
    }

    private void validateMessageCursor(
            Long beforeId,
            Long afterId,
            int size
    ) {
        boolean conflictingCursors = beforeId != null && afterId != null;
        boolean invalidBeforeId = beforeId != null && beforeId <= 0;
        boolean invalidAfterId = afterId != null && afterId < 0;
        boolean invalidSize = size < 1 || size > MAX_PAGE_SIZE;

        if (conflictingCursors || invalidBeforeId || invalidAfterId || invalidSize) {
            throw new InvalidChatMessageCursorException();
        }
    }

    private ChatRoomResponse toChatRoomResponse(
            ChatRoom chatRoom,
            Long memberId
    ) {
        Long friendId = chatRoom.getOtherMemberId(memberId);
        Member friend = memberRepository.findById(friendId).orElse(null);
        boolean canSend = friend != null && isAcceptedFriendship(memberId, friendId);
        long unreadCount = chatMessageRepository.countByRoomIdAndSenderIdNotAndIdGreaterThan(
                chatRoom.getId(),
                memberId,
                chatRoom.getLastReadMessageId(memberId)
        );

        return toChatRoomResponse(chatRoom, memberId, friend, canSend, unreadCount);
    }

    private ChatRoomResponse toChatRoomResponse(
            ChatRoom chatRoom,
            Long memberId,
            Member friend,
            boolean canSend,
            long unreadCount
    ) {
        String profileImageUrl = friend == null ? null : imageService.getUrl(friend.getProfileImageKey());

        return ChatRoomResponse.from(chatRoom, memberId, friend, profileImageUrl, canSend, unreadCount);
    }
}
