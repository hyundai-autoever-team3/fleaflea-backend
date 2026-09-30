package com.anabada.fleaflea.domain.friendship.repository;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository
        extends JpaRepository<Friendship, Long>, FriendshipRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Friendship f where f.status = :status and " +
            "((f.requester.memberId = :a and f.addressee.memberId = :b) or " +
            "(f.requester.memberId = :b and f.addressee.memberId = :a))")
    List<Friendship> lockRelationship(@Param("a") Long a, @Param("b") Long b,
                                    @Param("status") FriendshipStatus status);


    Optional<Friendship> findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
            Long requesterId,
            Long addresseeId,
            FriendshipStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Friendship> findByFriendshipIdAndStatus(
            Long friendshipId,
            FriendshipStatus status
    );

    boolean existsByRequester_MemberIdAndAddressee_MemberIdAndStatusIn(
            Long requesterId,
            Long addresseeId,
            Collection<FriendshipStatus> statuses
    );


    boolean existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
            Long requesterId,
            Long addresseeId,
            FriendshipStatus status
    );
}
