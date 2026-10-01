package com.anabada.fleaflea.domain.chat.service;

import com.anabada.fleaflea.domain.chat.domain.*;
import com.anabada.fleaflea.domain.chat.dto.ChatDtos.*;
import com.anabada.fleaflea.domain.chat.event.ChatEvent;
import com.anabada.fleaflea.domain.chat.repository.*;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
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
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final FriendshipRepository friendships;
    private final MemberRepository members;
    private final ImageService images;
    private final ApplicationEventPublisher events;

    @Transactional
    public Room open(Long memberId, Long friendId) {
        if (memberId.equals(friendId)) throw new BusinessException(CHAT_FRIEND_REQUIRED);
        long low = Math.min(memberId, friendId), high = Math.max(memberId, friendId);
        // A shared member row serializes concurrent creation before the unique constraint is reached.
        members.findLockedById(low).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));
        if (!members.existsById(high)) throw new BusinessException(MEMBER_NOT_FOUND);
        requireFriend(memberId, friendId);
        ChatRoom room = rooms.findByMemberLowIdAndMemberHighId(low, high)
                .orElseGet(() -> rooms.saveAndFlush(ChatRoom.create(low, high)));
        return describe(room, memberId);
    }

    public Rooms list(Long memberId, int page, int size) {
        var result = rooms.findForMember(memberId, PageRequest.of(page, size));
        if (result.isEmpty()) return new Rooms(List.of(), false);
        Map<Long, Member> friendsById = new HashMap<>();
        members.findAllById(result.stream().map(r -> r.otherMemberId(memberId)).toList())
                .forEach(m -> friendsById.put(m.getMemberId(), m));
        Set<Long> friendIds = new HashSet<>();
        friendships.findFriends(memberId).forEach(f -> friendIds.add(
                f.getRequester().getMemberId().equals(memberId) ? f.getAddressee().getMemberId() : f.getRequester().getMemberId()));
        Map<Long, Long> unread = new HashMap<>();
        messages.unreadCounts(result.stream().map(ChatRoom::getId).toList(), memberId)
                .forEach(c -> unread.put(c.getRoomId(), c.getUnreadCount()));
        return new Rooms(result.stream().map(r -> describe(r, memberId, friendsById.get(r.otherMemberId(memberId)),
                friendIds.contains(r.otherMemberId(memberId)), unread.getOrDefault(r.getId(), 0L))).toList(), result.hasNext());
    }

    public Room detail(Long memberId, Long roomId) {
        return describe(participant(memberId, roomId, false), memberId);
    }

    @Transactional
    public Message send(Long memberId, Long roomId, SendMessage request) {
        // This lock also makes the per-member persisted rate limit safe across concurrent requests.
        members.findLockedById(memberId).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));
        ChatRoom room = participant(memberId, roomId, true);
        requireFriend(memberId, room.otherMemberId(memberId));
        String content = request.content();
        if (content == null || content.isBlank() || content.length() > 2000 || request.clientMessageId() == null)
            throw new BusinessException(INVALID_REQUEST);
        String clientId = request.clientMessageId().toString();
        var previous = messages.findByRoomIdAndSenderIdAndClientMessageId(roomId, memberId, clientId);
        if (previous.isPresent()) {
            if (!previous.get().getContent().equals(content)) throw new BusinessException(CHAT_DUPLICATE_MESSAGE_CONFLICT);
            return Message.from(previous.get());
        }
        if (messages.recentCount(memberId, LocalDateTime.now().minusMinutes(1)) >= 60)
            throw new BusinessException(CHAT_RATE_LIMIT_EXCEEDED);
        ChatMessage saved = messages.saveAndFlush(ChatMessage.create(roomId, memberId, content, clientId));
        room.recordMessage(saved);
        Message response = Message.from(saved);
        events.publishEvent(new ChatEvent(memberId, room.otherMemberId(memberId), "chat-message", response));
        return response;
    }

    public Messages history(Long memberId, Long roomId, Long beforeId, Long afterId, int size) {
        participant(memberId, roomId, false);
        if (beforeId != null && afterId != null) throw new BusinessException(INVALID_REQUEST);
        if (size < 1 || size > 100 || (beforeId != null && beforeId <= 0) || (afterId != null && afterId < 0))
            throw new BusinessException(INVALID_REQUEST);
        List<ChatMessage> result = afterId == null
                ? messages.history(roomId, beforeId == null ? Long.MAX_VALUE : beforeId, PageRequest.of(0, size + 1))
                : messages.catchUp(roomId, afterId, PageRequest.of(0, size + 1));
        boolean hasNext = result.size() > size;
        List<Message> page = result.stream().limit(size).map(Message::from).toList();
        return new Messages(page, page.isEmpty() ? null : page.getLast().id(), hasNext);
    }

    @Transactional
    public ReadReceipt read(Long memberId, Long roomId, Long messageId) {
        ChatRoom room = participant(memberId, roomId, true);
        messages.findByIdAndRoomId(messageId, roomId).orElseThrow(() -> new BusinessException(CHAT_MESSAGE_NOT_FOUND));
        long previous = room.lastReadId(memberId);
        room.read(memberId, messageId);
        ReadReceipt receipt = new ReadReceipt(roomId, memberId, room.lastReadId(memberId));
        if (previous != receipt.lastReadMessageId())
            events.publishEvent(new ChatEvent(memberId, room.otherMemberId(memberId), "chat-read", receipt));
        return receipt;
    }

    private ChatRoom participant(Long memberId, Long roomId, boolean lock) {
        ChatRoom room = (lock ? rooms.findLockedById(roomId) : rooms.findById(roomId))
                .orElseThrow(() -> new BusinessException(CHAT_ROOM_NOT_FOUND));
        if (!room.hasMember(memberId)) throw new BusinessException(CHAT_NOT_PARTICIPANT);
        return room;
    }

    private void requireFriend(Long a, Long b) {
        // Friend deletion locks the same relationship row, so it cannot pass a message midway through removal.
        if (friendships.lockRelationship(a, b, FriendshipStatus.ACCEPTED).isEmpty())
            throw new BusinessException(CHAT_FRIEND_REQUIRED);
    }

    private boolean areFriends(Long a, Long b) {
        return friendships.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(a, b, FriendshipStatus.ACCEPTED)
                || friendships.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(b, a, FriendshipStatus.ACCEPTED);
    }

    private Room describe(ChatRoom room, Long memberId) {
        Long friendId = room.otherMemberId(memberId);
        Member friend = members.findById(friendId).orElse(null);
        return describe(room, memberId, friend, friend != null && areFriends(memberId, friendId),
                messages.countByRoomIdAndSenderIdNotAndIdGreaterThan(room.getId(), memberId, room.lastReadId(memberId)));
    }

    private Room describe(ChatRoom room, Long memberId, Member friend, boolean canSend, long unreadCount) {
        Long friendId = room.otherMemberId(memberId);
        return new Room(room.getId(), friendId, friend == null ? "탈퇴한 사용자" : friend.getNickname(),
                friend == null ? null : images.getUrl(friend.getProfileImageKey()),
                friend != null && canSend, room.getLastMessageId(), room.getLastMessageContent(),
                room.getLastMessageAt(), room.lastReadId(memberId), room.lastReadId(friendId),
                unreadCount);
    }
}
