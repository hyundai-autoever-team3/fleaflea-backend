package com.anabada.fleaflea.domain.friendship.repository;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository
        extends JpaRepository<Friendship, Long> {

    List<Friendship> findByAddressee_MemberIdAndStatus(
            Long memberId,
            FriendshipStatus status
    );

    List<Friendship> findByRequester_MemberIdAndStatus(
            Long memberId,
            FriendshipStatus status
    );

    Optional<Friendship>
    findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
            Long requesterId,
            Long addresseeId,
            FriendshipStatus status
    );

    Optional<Friendship> findByFriendshipIdAndStatus(
            Long friendshipId,
            FriendshipStatus status
    );

    boolean existsByRequester_MemberIdAndAddressee_MemberIdAndStatusIn(
            Long requesterId,
            Long addresseeId,
            Collection<FriendshipStatus> statuses
    );

    @Query("""
            SELECT f
            FROM Friendship f
            JOIN FETCH f.requester
            JOIN FETCH f.addressee
            WHERE f.status = :status
              AND (
                    f.requester.memberId = :memberId
                    OR f.addressee.memberId = :memberId
              )
            """)
    List<Friendship> findByFriendships(
            @Param("memberId") Long memberId,
            @Param("status") FriendshipStatus status
    );

    boolean existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
            Long requesterId,
            Long addresseeId,
            FriendshipStatus status
    );
}