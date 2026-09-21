package com.anabada.fleaflea.domain.friendship.repository;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;

import java.util.Collection;
import java.util.List;

public interface FriendshipRepositoryCustom {
    List<Friendship> findReceivedRequests(Long memberId);

    List<Friendship> findSentRequests(Long memberId);

    List<Friendship> findFriends(Long memberId);

    List<Friendship> findActiveRelationships(
            Long memberId,
            Collection<Long> targetMemberIds
    );
}
