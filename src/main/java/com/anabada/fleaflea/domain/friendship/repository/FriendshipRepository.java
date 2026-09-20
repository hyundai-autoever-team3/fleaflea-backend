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
        extends JpaRepository<Friendship, Long>, FriendshipRepositoryCustom {


    Optional<Friendship> findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
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


    boolean existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
            Long requesterId,
            Long addresseeId,
            FriendshipStatus status
    );
}