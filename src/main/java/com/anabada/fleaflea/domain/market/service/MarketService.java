package com.anabada.fleaflea.domain.market.service;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.dto.*;
import com.anabada.fleaflea.domain.market.exception.MarketNotFoundException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageCategory;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketService {

    private final MarketRepository marketRepository;
    private final MarketMemberRepository marketMemberRepository;
    private final MemberRepository memberRepository;
    private final ImageService imageService;

    @Transactional
    public MarketCreateResponse createMarket(
            Long memberId,
            MarketCreateRequest request
    ) {
        Member host = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        String inviteCode = generateUniqueInviteCode();

        String coverImageKey = null;

        if (request.coverImage() != null
                && !request.coverImage().isEmpty()) {
            coverImageKey = imageService.upload(
                    request.coverImage(),
                    ImageCategory.MARKET
            );
        }

        Market market = Market.create(
                host,
                request.title(),
                request.description(),
                coverImageKey,
                inviteCode
        );

        Market savedMarket = marketRepository.save(market);

        MarketMember hostMembership = MarketMember.create(
                savedMarket,
                host
        );

        marketMemberRepository.save(hostMembership);

        String coverImageUrl = imageService.getUrl(
                savedMarket.getCoverImageKey()
        );

        return MarketCreateResponse.from(
                savedMarket,
                coverImageUrl
        );
    }

    private String generateUniqueInviteCode() {
        String inviteCode;

        do {
            inviteCode = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 10)
                    .toUpperCase();
        } while (marketRepository.existsByInviteCode(inviteCode));

        return inviteCode;
    }

    @Transactional
    public MarketUpdateResponse updateMarket(
            Long memberId,
            Long marketId,
            MarketUpdateRequest request
    ) {
        Market market = getMarket(marketId);

        validateHost(market, memberId);

        String coverImageKey = market.getCoverImageKey();

        if (request.coverImage() != null
                && !request.coverImage().isEmpty()) {
            if (coverImageKey == null) {
                coverImageKey = imageService.upload(
                        request.coverImage(),
                        ImageCategory.MARKET
                );
            } else {
                coverImageKey = imageService.replace(
                        coverImageKey,
                        request.coverImage(),
                        ImageCategory.MARKET
                );
            }
        }

        market.update(
                request.title(),
                request.description(),
                coverImageKey
        );

        String coverImageUrl =
                imageService.getUrl(market.getCoverImageKey());

        return MarketUpdateResponse.from(
                market,
                coverImageUrl
        );
    }

    public MarketInvitationResponse getInvitation(
            Long memberId,
            Long marketId
    ) {
        Market market = getMarket(marketId);

        validateHost(market, memberId);

        return new MarketInvitationResponse(
                market.getMarketId(),
                market.getInviteCode()
        );
    }

    @Transactional
    public MarketInvitationResponse reissueInvitation(
            Long memberId,
            Long marketId
    ) {
        Market market = getMarket(marketId);

        validateHost(market, memberId);

        String inviteCode = generateUniqueInviteCode();
        market.changeInviteCode(inviteCode);

        return new MarketInvitationResponse(
                market.getMarketId(),
                inviteCode
        );
    }

    @Transactional
    public void leaveMarket(
            Long memberId,
            Long marketId
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        Market market = getMarket(marketId);

        if (market.getHost().getMemberId().equals(memberId)) {
            throw new BusinessException(
                    ErrorCode.MARKET_HOST_CANNOT_LEAVE
            );
        }

        MarketMember membership = marketMemberRepository
                .findByMarketAndMember(market, member)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MARKET_MEMBERSHIP_NOT_FOUND
                ));

        marketMemberRepository.delete(membership);
    }

    @Transactional
    public void deleteMarket(
            Long memberId,
            Long marketId
    ) {
        Market market = getMarket(marketId);

        validateHost(market, memberId);

        String coverImageKey = market.getCoverImageKey();

        marketMemberRepository.deleteAllByMarket(market);
        marketRepository.delete(market);

        if (coverImageKey != null) {
            imageService.delete(coverImageKey);
        }
    }

    private Market getMarket(Long marketId) {
        return marketRepository.findById(marketId)
                .orElseThrow(MarketNotFoundException::new);
    }

    private void validateHost(
            Market market,
            Long memberId
    ) {
        if (!market.getHost().getMemberId().equals(memberId)) {
            throw new BusinessException(
                    ErrorCode.MARKET_HOST_ONLY
            );
        }
    }
}
