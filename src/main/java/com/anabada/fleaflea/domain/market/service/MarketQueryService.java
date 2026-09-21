package com.anabada.fleaflea.domain.market.service;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.domain.RelationshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.dto.MarketDetailResponse;
import com.anabada.fleaflea.domain.market.dto.MarketSummaryResponse;
import com.anabada.fleaflea.domain.market.dto.MarketSearchCondition;
import com.anabada.fleaflea.domain.market.exception.MarketAccessDeniedException;
import com.anabada.fleaflea.domain.market.exception.MarketNotFoundException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.dto.MarketMemberResponse;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketQueryService {

    private final MarketMemberRepository marketMemberRepository;
    private final MemberRepository memberRepository;
    private final MarketRepository marketRepository;
    private final FriendshipRepository friendshipRepository;
    private final ImageService imageService;

    public PageResponse<MarketSummaryResponse> getMarkets(
            Long memberId,
            String scope,
            MarketSearchCondition condition,
            Pageable pageable
    ) {
        if (!"joined".equals(scope)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        return PageResponse.from(
                marketMemberRepository
                        .searchJoinedMarkets(
                                member.getMemberId(),
                                condition,
                                pageable
                        )
                        .map(projection -> {
                            String coverImageUrl = imageService.getUrl(
                                    projection.coverImageKey()
                            );

                            return MarketSummaryResponse.from(
                                    projection,
                                    coverImageUrl
                            );
                        })
        );
    }

    public MarketDetailResponse getMarket(
            Long memberId,
            Long marketId
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        Market market = marketRepository.findById(marketId)
                .orElseThrow(MarketNotFoundException::new);

        if (!marketMemberRepository.existsByMarketAndMember(
                market,
                member
        )) {
            throw new MarketAccessDeniedException();
        }

        long memberCount = marketMemberRepository.countByMarket(market);

        String coverImageUrl = imageService.getUrl(
                market.getCoverImageKey()
        );

        return MarketDetailResponse.from(
                market,
                memberCount,
                coverImageUrl
        );
    }

    public PageResponse<MarketMemberResponse> getMarketMembers(
            Long memberId,
            Long marketId,
            Pageable pageable
    ) {
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        Market market = marketRepository.findById(marketId)
                .orElseThrow(MarketNotFoundException::new);

        if (!marketMemberRepository.existsByMarketAndMember(
                market,
                requester
        )) {
            throw new MarketAccessDeniedException();
        }

        Long hostId = market.getHost().getMemberId();
        Page<MarketMember> marketMembers =
                marketMemberRepository.findAllByMarket(market, pageable);

        Map<Long, RelationshipStatus> relationshipStatuses =
                getRelationshipStatuses(memberId, marketMembers.getContent());

        return PageResponse.from(
                marketMembers
                        .map(marketMember -> toMarketMemberResponse(
                                marketMember,
                                hostId,
                                relationshipStatuses.getOrDefault(
                                        marketMember.getMember().getMemberId(),
                                        RelationshipStatus.NONE
                                )
                        ))
        );
    }

    private Map<Long, RelationshipStatus> getRelationshipStatuses(
            Long memberId,
            List<MarketMember> marketMembers
    ) {
        Map<Long, RelationshipStatus> statuses = new HashMap<>();
        statuses.put(memberId, RelationshipStatus.SELF);

        List<Long> targetMemberIds = marketMembers.stream()
                .map(marketMember -> marketMember.getMember().getMemberId())
                .filter(targetMemberId -> !targetMemberId.equals(memberId))
                .toList();

        if (targetMemberIds.isEmpty()) {
            return statuses;
        }

        friendshipRepository
                .findActiveRelationships(memberId, targetMemberIds)
                .forEach(friendship -> statuses.put(
                        getOtherMemberId(friendship, memberId),
                        getRelationshipStatus(friendship, memberId)
                ));

        return statuses;
    }

    private Long getOtherMemberId(Friendship friendship, Long memberId) {
        if (friendship.getRequester().getMemberId().equals(memberId)) {
            return friendship.getAddressee().getMemberId();
        }
        return friendship.getRequester().getMemberId();
    }

    private RelationshipStatus getRelationshipStatus(
            Friendship friendship,
            Long memberId
    ) {
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return RelationshipStatus.FRIEND;
        }
        if (friendship.getRequester().getMemberId().equals(memberId)) {
            return RelationshipStatus.REQUESTED;
        }
        return RelationshipStatus.REQUEST_RECEIVED;
    }

    private MarketMemberResponse toMarketMemberResponse(
            MarketMember marketMember,
            Long hostId,
            RelationshipStatus relationshipStatus
    ) {
        String profileImageUrl = imageService.getUrl(
                marketMember.getMember().getProfileImageKey()
        );

        return MarketMemberResponse.from(
                marketMember,
                profileImageUrl,
                hostId,
                relationshipStatus
        );
    }
}
