package com.anabada.fleaflea.domain.chat.repository;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByRoomIdAndSenderIdAndClientMessageId(
            Long roomId,
            Long senderId,
            String clientMessageId
    );

    Optional<ChatMessage> findByIdAndRoomId(Long id, Long roomId);

    @Query("""
            select message from ChatMessage message
            where message.roomId = :roomId and message.id < :beforeId
            order by message.id desc
            """)
    List<ChatMessage> findMessagesBeforeId(
            @Param("roomId") Long roomId,
            @Param("beforeId") long beforeId,
            Pageable pageable
    );

    @Query("""
            select message from ChatMessage message
            where message.roomId = :roomId and message.id > :afterId
            order by message.id asc
            """)
    List<ChatMessage> findMessagesAfterId(
            @Param("roomId") Long roomId,
            @Param("afterId") long afterId,
            Pageable pageable
    );

    long countByRoomIdAndSenderIdNotAndIdGreaterThan(Long roomId, Long senderId, long lastReadId);

    interface UnreadCount {

        Long getRoomId();

        Long getUnreadCount();
    }

    @Query("""
            select message.roomId as roomId, count(message) as unreadCount
            from ChatMessage message, ChatRoom chatRoom
            where chatRoom.id = message.roomId
                and chatRoom.id in :roomIds
                and message.senderId <> :memberId
                and message.id > case when chatRoom.memberLowId = :memberId
                    then chatRoom.lowLastReadId else chatRoom.highLastReadId end
            group by message.roomId
            """)
    List<UnreadCount> countUnreadMessagesByRoomIds(
            @Param("roomIds") List<Long> roomIds,
            @Param("memberId") Long memberId
    );

    @Query("select count(m) from ChatMessage m where m.senderId = :senderId and m.createdAt >= :since")
    long countRecentMessagesBySenderId(
            @Param("senderId") Long senderId,
            @Param("since") LocalDateTime since
    );
}
