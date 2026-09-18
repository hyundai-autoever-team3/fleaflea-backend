package com.anabada.fleaflea.domain.friendship.service;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.domain.RelationshipStatus;
import com.anabada.fleaflea.domain.friendship.dto.FriendshipResponse;
import com.anabada.fleaflea.domain.friendship.event.FriendAcceptedEvent;
import com.anabada.fleaflea.domain.friendship.event.FriendRequestedEvent;
import com.anabada.fleaflea.domain.friendship.exception.FriendshipAlreadyExistsException;
import com.anabada.fleaflea.domain.friendship.exception.FriendshipNotFoundException;
import com.anabada.fleaflea.domain.friendship.exception.SelfFriendRequestException;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final MemberRepository memberRepository;
    private final ImageService imageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<FriendshipResponse> getReceivedRequests(Long memberId) {
        List<Friendship> friendships =
                friendshipRepository.findByAddressee_MemberIdAndStatus(
                        memberId,
                        FriendshipStatus.PENDING
                );

        return friendships.stream()
                .map(friendship -> {
                    Member member = friendship.getRequester();

                    String profileImageUrl =
                            imageService.getUrl(
                                    member.getProfileImageKey()
                            );

                    return FriendshipResponse.from(
                            friendship,
                            member,
                            profileImageUrl,
                            RelationshipStatus.REQUEST_RECEIVED
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> getSentRequests(Long memberId) {
        List<Friendship> friendships =
                friendshipRepository.findByRequester_MemberIdAndStatus(
                        memberId,
                        FriendshipStatus.PENDING
                );

        return friendships.stream()
                .map(friendship -> {
                    Member member = friendship.getAddressee();

                    String profileImageUrl =
                            imageService.getUrl(
                                    member.getProfileImageKey()
                            );

                    return FriendshipResponse.from(
                            friendship,
                            member,
                            profileImageUrl,
                            RelationshipStatus.REQUESTED
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> getMyFriends(Long memberId) {
        List<Friendship> friendships =
                friendshipRepository.findByFriendships(
                        memberId,
                        FriendshipStatus.ACCEPTED
                );

        return friendships.stream()
                .map(friendship -> {
                    Member member;

                    if (friendship.getRequester()
                            .getMemberId()
                            .equals(memberId)) {
                        member = friendship.getAddressee();
                    } else {
                        member = friendship.getRequester();
                    }

                    String profileImageUrl =
                            imageService.getUrl(
                                    member.getProfileImageKey()
                            );

                    return FriendshipResponse.from(
                            friendship,
                            member,
                            profileImageUrl,
                            RelationshipStatus.FRIEND
                    );
                })
                .toList();
    }

    @Transactional
    public void requestFollow(
            Long memberId,
            Long targetMemberId
    ) {
        if (memberId.equals(targetMemberId)) {
            throw new SelfFriendRequestException();
        }

        Member requester = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        Member addressee =
                memberRepository.findById(targetMemberId)
                        .orElseThrow(MemberNotFoundException::new);

        List<FriendshipStatus> activeStatuses =
                List.of(
                        FriendshipStatus.PENDING,
                        FriendshipStatus.ACCEPTED
                );

        boolean forwardExists =
                friendshipRepository
                        .existsByRequester_MemberIdAndAddressee_MemberIdAndStatusIn(
                                memberId,
                                targetMemberId,
                                activeStatuses
                        );

        boolean reverseExists =
                friendshipRepository
                        .existsByRequester_MemberIdAndAddressee_MemberIdAndStatusIn(
                                targetMemberId,
                                memberId,
                                activeStatuses
                        );

        if (forwardExists || reverseExists) {
            throw new FriendshipAlreadyExistsException();
        }

        Friendship friendship = Friendship.create(
                requester,
                addressee,
                FriendshipStatus.PENDING
        );

        friendshipRepository.save(friendship);

        eventPublisher.publishEvent(
                FriendRequestedEvent.of(
                        friendship.getFriendshipId(),
                        requester.getMemberId(),
                        addressee.getMemberId(),
                        requester.getNickname()
                )
        );
    }

    @Transactional
    public void acceptFollow(
            Long memberId,
            Long requesterId
    ) {
        Friendship friendship =
                friendshipRepository
                        .findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                                requesterId,
                                memberId,
                                FriendshipStatus.PENDING
                        )
                        .orElseThrow(
                                FriendshipNotFoundException::new
                        );

        friendship.accept();

        Member requester = friendship.getRequester();
        Member addressee = friendship.getAddressee();

        eventPublisher.publishEvent(
                FriendAcceptedEvent.of(
                        friendship.getFriendshipId(),
                        requester.getMemberId(),
                        addressee.getMemberId(),
                        addressee.getNickname()
                )
        );
    }

    @Transactional
    public void rejectFollow(
            Long memberId,
            Long requesterId
    ) {
        Friendship friendship =
                friendshipRepository
                        .findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                                requesterId,
                                memberId,
                                FriendshipStatus.PENDING
                        )
                        .orElseThrow(
                                FriendshipNotFoundException::new
                        );

        friendship.reject();
    }

    @Transactional
    public void cancelFollow(
            Long memberId,
            Long addresseeId
    ) {
        Friendship friendship =
                friendshipRepository
                        .findByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                                memberId,
                                addresseeId,
                                FriendshipStatus.PENDING
                        )
                        .orElseThrow(
                                FriendshipNotFoundException::new
                        );

        friendship.cancel();
    }

    @Transactional
    public void deleteFriend(
            Long memberId,
            Long friendshipId
    ) {
        Friendship friendship =
                friendshipRepository
                        .findByFriendshipIdAndStatus(
                                friendshipId,
                                FriendshipStatus.ACCEPTED
                        )
                        .orElseThrow(
                                FriendshipNotFoundException::new
                        );

        if (!friendship.isParticipant(memberId)) {
            throw new FriendshipNotFoundException();
        }

        friendshipRepository.delete(friendship);
    }
}