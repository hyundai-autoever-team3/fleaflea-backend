package com.anabada.fleaflea.domain.chat.repository;

import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByMemberLowIdAndMemberHighId(Long low, Long high);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    Optional<ChatRoom> findLockedById(@Param("id") Long id);

    @Query("select r from ChatRoom r where r.memberLowId = :memberId or r.memberHighId = :memberId order by r.updatedAt desc, r.id desc")
    Slice<ChatRoom> findForMember(@Param("memberId") Long memberId, Pageable pageable);
}
