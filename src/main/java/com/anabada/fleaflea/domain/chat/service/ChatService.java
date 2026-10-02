package com.anabada.fleaflea.domain.chat.service;

import com.anabada.fleaflea.domain.chat.domain.*;
import com.anabada.fleaflea.domain.chat.exception.*;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomListResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomResponse;
import com.anabada.fleaflea.domain.chat.event.ChatEvent;
import com.anabada.fleaflea.domain.chat.repository.*;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.dto.CursorPageResponse;
import com.anabada.fleaflea.global.exception.*;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static com.anabada.fleaflea.global.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final FriendshipRepository friendshipRepository;
    private final MemberRepository memberRepository;
    private final ImageService imageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatRoomResponse open(Long memberId, Long friendId) {
        if (memberId.equals(friendId)) throw new ChatFriendRequiredException();
        long low = Math.min(memberId, friendId), high = Math.max(memberId, friendId);
        // A shared member row serializes concurrent creation before the unique constraint is reached.
        memberRepository.findLockedById(low).orElseThrow(MemberNotFoundException::new);
        if (!memberRepository.existsById(high)) throw new MemberNotFoundException();
        requireFriend(memberId, friendId);
        ChatRoom room = chatRoomRepository.findByMemberLowIdAndMemberHighId(low, high)
                .orElseGet(() -> chatRoomRepository.saveAndFlush(ChatRoom.create(low, high)));
        return describe(room, memberId);
    }

    public ChatRoomListResponse list(Long memberId, int page, int size) {
        var result = chatRoomRepository.findForMember(memberId, PageRequest.of(page, size));
        if (result.isEmpty()) return new ChatRoomListResponse(List.of(), false);
        Map<Long, Member> friendsById = new HashMap<>();
        memberRepository.findAllById(result.stream().map(r -> r.otherMemberId(memberId)).toList())
                .forEach(m -> friendsById.put(m.getMemberId(), m));
        Set<Long> friendIds = new HashSet<>();
        friendshipRepository.findFriends(memberId).forEach(f -> friendIds.add(
                f.getRequester().getMemberId().equals(memberId) ? f.getAddressee().getMemberId() : f.getRequester().getMemberId()));
        Map<Long, Long> unread = new HashMap<>();
        chatMessageRepository.unreadCounts(result.stream().map(ChatRoom::getId).toList(), memberId)
                .forEach(c -> unread.put(c.getRoomId(), c.getUnreadCount()));
        return new ChatRoomListResponse(result.stream().map(r -> describe(r, memberId, friendsById.get(r.otherMemberId(memberId)),
                friendIds.contains(r.otherMemberId(memberId)), unread.getOrDefault(r.getId(), 0L))).toList(), result.hasNext());
    }

    public ChatRoomResponse detail(Long memberId, Long roomId) {
        return describe(participant(memberId, roomId, false), memberId);
    }

    @Transactional
    public ChatMessageResponse send(Long memberId, Long roomId, ChatMessageSendRequest request) {
        // This lock also makes the per-member persisted rate limit safe across concurrent requests.
        memberRepository.findLockedById(memberId).orElseThrow(MemberNotFoundException::new);
        ChatRoom room = participant(memberId, roomId, true);
        requireFriend(memberId, room.otherMemberId(memberId));
        String content = request.content();
        String clientId = request.clientMessageId().toString();
        var previous = chatMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(roomId, memberId, clientId);
        if (previous.isPresent()) {
            if (!previous.get().getContent().equals(content)) throw new ChatDuplicateMessageConflictException();
            return ChatMessageResponse.from(previous.get());
        }
        if (chatMessageRepository.recentCount(memberId, LocalDateTime.now().minusMinutes(1)) >= 60)
            throw new ChatRateLimitExceededException();
        ChatMessage saved = chatMessageRepository.saveAndFlush(ChatMessage.create(roomId, memberId, content, clientId));
        room.recordMessage(saved);
        ChatMessageResponse response = ChatMessageResponse.from(saved);
        eventPublisher.publishEvent(new ChatEvent(memberId, room.otherMemberId(memberId), "chat-message", response));
        return response;
    }

    public CursorPageResponse<ChatMessageResponse> history(Long memberId, Long roomId, Long beforeId, Long afterId, int size) {
        participant(memberId, roomId, false);
        if (beforeId != null && afterId != null) throw new BusinessException(INVALID_REQUEST);
        if (size < 1 || size > 100 || (beforeId != null && beforeId <= 0) || (afterId != null && afterId < 0))
            throw new BusinessException(INVALID_REQUEST);
        List<ChatMessage> result = afterId == null
                ? chatMessageRepository.history(roomId, beforeId == null ? Long.MAX_VALUE : beforeId, PageRequest.of(0, size + 1))
                : chatMessageRepository.catchUp(roomId, afterId, PageRequest.of(0, size + 1));
        boolean hasNext = result.size() > size;
        List<ChatMessageResponse> page = result.stream().limit(size).map(ChatMessageResponse::from).toList();
        return new CursorPageResponse<>(page, page.isEmpty() ? null : page.getLast().id(), hasNext);
    }

    @Transactional
    public ChatReadResponse read(Long memberId, Long roomId, Long messageId) {
        ChatRoom room = participant(memberId, roomId, true);
        chatMessageRepository.findByIdAndRoomId(messageId, roomId).orElseThrow(ChatMessageNotFoundException::new);
        long previous = room.lastReadId(memberId);
        room.read(memberId, messageId);
        ChatReadResponse receipt = new ChatReadResponse(roomId, memberId, room.lastReadId(memberId));
        if (previous != receipt.lastReadMessageId())
            eventPublisher.publishEvent(new ChatEvent(memberId, room.otherMemberId(memberId), "chat-read", receipt));
        return receipt;
    }

    private ChatRoom participant(Long memberId, Long roomId, boolean lock) {
        ChatRoom room = (lock ? chatRoomRepository.findLockedById(roomId) : chatRoomRepository.findById(roomId))
                .orElseThrow(ChatRoomNotFoundException::new);
        if (!room.hasMember(memberId)) throw new ChatNotParticipantException();
        return room;
    }

    private void requireFriend(Long a, Long b) {
        // Friend deletion locks the same relationship row, so it cannot pass a message midway through removal.
        if (friendshipRepository.lockRelationship(a, b, FriendshipStatus.ACCEPTED).isEmpty())
            throw new ChatFriendRequiredException();
    }

    private boolean areFriends(Long a, Long b) {
        return friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(a, b, FriendshipStatus.ACCEPTED)
                || friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(b, a, FriendshipStatus.ACCEPTED);
    }

    private ChatRoomResponse describe(ChatRoom room, Long memberId) {
        Long friendId = room.otherMemberId(memberId);
        Member friend = memberRepository.findById(friendId).orElse(null);
        return describe(room, memberId, friend, friend != null && areFriends(memberId, friendId),
                chatMessageRepository.countByRoomIdAndSenderIdNotAndIdGreaterThan(room.getId(), memberId, room.lastReadId(memberId)));
    }

    private ChatRoomResponse describe(ChatRoom room, Long memberId, Member friend, boolean canSend, long unreadCount) {
        Long friendId = room.otherMemberId(memberId);
        return new ChatRoomResponse(room.getId(), friendId, friend == null ? "탈퇴한 사용자" : friend.getNickname(),
                friend == null ? null : imageService.getUrl(friend.getProfileImageKey()),
                friend != null && canSend, room.getLastMessageId(), room.getLastMessageContent(),
                room.getLastMessageAt(), room.lastReadId(memberId), room.lastReadId(friendId),
                unreadCount);
    }
}
