package com.anabada.fleaflea.domain.chat.repository;

import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByMemberLowIdAndMemberHighId(Long memberLowId, Long memberHighId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    Optional<ChatRoom> findLockedById(@Param("id") Long id);

    @Query("""
            select chatRoom from ChatRoom chatRoom
            where chatRoom.memberLowId = :memberId or chatRoom.memberHighId = :memberId
            order by coalesce(chatRoom.lastMessageAt, chatRoom.createdAt) desc, chatRoom.id desc
            """)
    Slice<ChatRoom> findChatRoomsByMemberId(@Param("memberId") Long memberId, Pageable pageable);
}
