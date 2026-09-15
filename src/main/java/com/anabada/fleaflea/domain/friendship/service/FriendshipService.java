package com.anabada.fleaflea.domain.friendship.service;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.dto.FriendshipResponse;
import com.anabada.fleaflea.domain.friendship.exception.FriendshipNotFoundException;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final MemberRepository memberRepository;

    public List<FriendshipResponse> getReceivedRequests(Long memberId) {
        List<Friendship> friendships = friendshipRepository.findByAddressee_MemberIdAndStatus(
                memberId,
                FriendshipStatus.PENDING
        );
        return friendships.stream().map(friendship ->
                FriendshipResponse.from(friendship.getRequester()))
                .toList();
    }

    public List<FriendshipResponse> getSentRequests(Long memberId) {
        List<Friendship> friendships = friendshipRepository.findByRequester_MemberIdAndStatus(
                memberId,
                FriendshipStatus.PENDING
        );
        return friendships.stream().map(friendship ->
                FriendshipResponse.from(friendship.getAddressee()))
                .toList();

    }

    public List<FriendshipResponse> getMyFriends(Long memberId) {
        List<Friendship> friendships = friendshipRepository.findByFriendships(
                memberId,
                FriendshipStatus.ACCEPTED
        );
        return friendships.stream().map(friendship -> {
                    if (friendship.getRequester().getMemberId().equals(memberId)) {
                        return FriendshipResponse.from(friendship.getAddressee());
                    }
                    return FriendshipResponse.from(friendship.getRequester());
                })
                .toList();
    }

    public void requestFollow(Long memberId, Long targetMemberId) {
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
        Member addressee = memberRepository.findById(targetMemberId)
                .orElseThrow(MemberNotFoundException::new);

        Friendship friendship = Friendship.create(
                requester,
                addressee,
                FriendshipStatus.PENDING
        );

        friendshipRepository.save(friendship);
    }

    @Transactional
    public void acceptFollow(Long memberId, Long requesterId) {
        Friendship friendship =
                friendshipRepository
                        .findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                                requesterId,
                                memberId,
                                FriendshipStatus.PENDING
                        )
                        .orElseThrow(FriendshipNotFoundException::new);
        friendship.accept();
    }

    @Transactional
    public void rejectFollow(Long memberId, Long requesterId) {
        Friendship friendship =
                friendshipRepository
                        .findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                                requesterId,
                                memberId,
                                FriendshipStatus.PENDING
                        )
                        .orElseThrow(FriendshipNotFoundException::new);
        friendship.reject();
    }

    @Transactional
    public void cancelFollow(Long memberId, Long requesterId) {
        Friendship friendship =
                friendshipRepository
                        .findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                                requesterId,
                                memberId,
                                FriendshipStatus.PENDING
                        )
                        .orElseThrow(FriendshipNotFoundException::new);
        friendship.cancel();
    }

}
