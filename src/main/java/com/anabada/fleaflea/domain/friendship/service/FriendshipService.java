package com.anabada.fleaflea.domain.friendship.service;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.dto.FriendshipResponse;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;

    public List<FriendshipResponse> getReceivedRequests(Long memberId) {
        List<Friendship> friendships = friendshipRepository.findByAddressee_MemberIdAndStatus(
                memberId,
                FriendshipStatus.PENDING
        );
        return friendships.stream().map(friendship ->
                FriendshipResponse.from(friendship.getRequester()))
                .toList();
    }


}
