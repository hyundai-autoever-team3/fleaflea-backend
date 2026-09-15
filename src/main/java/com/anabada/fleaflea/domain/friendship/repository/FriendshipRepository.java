package com.anabada.fleaflea.domain.friendship.repository;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendshipRepository
        extends JpaRepository<Friendship, Long> {

    boolean existsByRequesterIdAndAddresseeIdAndStatus(
            Long requesterId,
            Long addresseeId,
            FriendshipStatus status
    );
}