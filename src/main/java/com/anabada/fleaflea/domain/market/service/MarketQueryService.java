package com.anabada.fleaflea.domain.market.service;

import com.anabada.fleaflea.domain.market.dto.MarketSummaryResponse;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.dto.MarketDetailResponse;
import com.anabada.fleaflea.domain.market.exception.MarketAccessDeniedException;
import com.anabada.fleaflea.domain.market.exception.MarketNotFoundException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;

import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.dto.MarketMemberResponse;
import com.anabada.fleaflea.global.image.ImageService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketQueryService {

    private final MarketMemberRepository marketMemberRepository;
    private final MemberRepository memberRepository;
    private final MarketRepository marketRepository;
    private final ImageService imageService;

    public PageResponse<MarketSummaryResponse> getMarkets(
            Long memberId,
            String scope,
            Pageable pageable
    ) {
        if (!"joined".equals(scope)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        return PageResponse.from(
                marketMemberRepository
                        .findAllByMember(member, pageable)
                        .map(MarketSummaryResponse::from)
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

        return MarketDetailResponse.from(
                market,
                memberCount
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

        return PageResponse.from(
                marketMemberRepository
                        .findAllByMarket(market, pageable)
                        .map(marketMember -> toMarketMemberResponse(
                                marketMember,
                                hostId
                        ))
        );
    }

    private MarketMemberResponse toMarketMemberResponse(
            MarketMember marketMember,
            Long hostId
    ) {
        String profileImageUrl = imageService.getUrl(
                marketMember.getMember().getProfileImageKey()
        );

        return MarketMemberResponse.from(
                marketMember,
                profileImageUrl,
                hostId
        );
    }
}
