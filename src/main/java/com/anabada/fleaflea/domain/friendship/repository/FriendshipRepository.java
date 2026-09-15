package com.anabada.fleaflea.domain.friendship.repository;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    // 받은 요청
    List<Friendship> findByAddressee_MemberIdAndStatus(
            Long memberId,
            FriendshipStatus status
    );
    // 보낸 요청
    List<Friendship> findByRequester_MemberIdAndStatus(
            Long memberId,
            FriendshipStatus status
    );

    @Query("""
            SELECT f
            FROM Friendship f
            WHERE f.status = :status
            AND (f.requester.memberId = :memberId
            OR f.addressee.memberId = :memberId)
           """)
    List<Friendship> findByFriendships(
            @Param("memberId") Long memberId,
            @Param("status") FriendshipStatus status
    );

    Optional<Friendship> findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
            Long requesterId,
            Long addresseeId,
            FriendshipStatus status
    );


}
