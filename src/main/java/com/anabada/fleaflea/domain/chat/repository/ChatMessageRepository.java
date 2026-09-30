package com.anabada.fleaflea.domain.chat.repository;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId, String clientMessageId);
    Optional<ChatMessage> findByIdAndRoomId(Long id, Long roomId);

    @Query("select m from ChatMessage m where m.roomId = :roomId and m.id < :beforeId order by m.id desc")
    List<ChatMessage> history(@Param("roomId") Long roomId, @Param("beforeId") long beforeId, Pageable pageable);

    @Query("select m from ChatMessage m where m.roomId = :roomId and m.id > :afterId order by m.id asc")
    List<ChatMessage> catchUp(@Param("roomId") Long roomId, @Param("afterId") long afterId, Pageable pageable);

    long countByRoomIdAndSenderIdNotAndIdGreaterThan(Long roomId, Long senderId, long lastReadId);

    interface UnreadCount {
        Long getRoomId();
        Long getUnreadCount();
    }

    @Query("select m.roomId as roomId, count(m) as unreadCount from ChatMessage m, ChatRoom r " +
            "where r.id = m.roomId and r.id in :roomIds and m.senderId <> :memberId " +
            "and m.id > case when r.memberLowId = :memberId then r.lowLastReadId else r.highLastReadId end " +
            "group by m.roomId")
    List<UnreadCount> unreadCounts(@Param("roomIds") List<Long> roomIds, @Param("memberId") Long memberId);

    @Query("select count(m) from ChatMessage m where m.senderId = :senderId and m.createdAt >= :since")
    long recentCount(@Param("senderId") Long senderId, @Param("since") java.time.LocalDateTime since);
}
