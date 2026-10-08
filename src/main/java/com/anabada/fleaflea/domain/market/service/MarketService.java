package com.anabada.fleaflea.domain.market.service;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.dto.MarketCreateRequest;
import com.anabada.fleaflea.domain.market.dto.MarketCreateResponse;
import com.anabada.fleaflea.domain.market.dto.MarketInvitationResponse;
import com.anabada.fleaflea.domain.market.dto.MarketUpdateRequest;
import com.anabada.fleaflea.domain.market.dto.MarketUpdateResponse;
import com.anabada.fleaflea.domain.market.exception.MarketHostCannotLeaveException;
import com.anabada.fleaflea.domain.market.exception.MarketHostOnlyException;
import com.anabada.fleaflea.domain.market.exception.MarketMembershipNotFoundException;
import com.anabada.fleaflea.domain.market.exception.MarketNotFoundException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.image.ImageCategory;
import com.anabada.fleaflea.global.image.ImageService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            MarketCreateRequest marketCreateRequest
    ) {
        Member host = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        String inviteCode = generateUniqueInviteCode();

        String coverImageKey = null;

        if (marketCreateRequest.coverImage() != null
                && !marketCreateRequest.coverImage().isEmpty()) {
            coverImageKey = imageService.uploadInTransaction(
                    marketCreateRequest.coverImage(),
                    ImageCategory.MARKET
            );
        }

        Market market = Market.create(
                host,
                marketCreateRequest.title(),
                marketCreateRequest.description(),
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
            MarketUpdateRequest marketUpdateRequest
    ) {
        Market market = getMarket(marketId);

        validateHost(market, memberId);

        String coverImageKey = market.getCoverImageKey();

        if (marketUpdateRequest.coverImage() != null
                && !marketUpdateRequest.coverImage().isEmpty()) {
            if (coverImageKey == null) {
                coverImageKey = imageService.uploadInTransaction(
                        marketUpdateRequest.coverImage(),
                        ImageCategory.MARKET
                );
            } else {
                coverImageKey = imageService.replace(
                        coverImageKey,
                        marketUpdateRequest.coverImage(),
                        ImageCategory.MARKET
                );
            }
        }

        market.update(
                marketUpdateRequest.title(),
                marketUpdateRequest.description(),
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

        return MarketInvitationResponse.from(market);
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

        return MarketInvitationResponse.from(market);
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
            throw new MarketHostCannotLeaveException();
        }

        MarketMember membership = marketMemberRepository
                .findByMarketAndMember(market, member)
                .orElseThrow(() -> new MarketMembershipNotFoundException());

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
        imageService.deleteAfterCommit(coverImageKey);
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
            throw new MarketHostOnlyException();
        }
    }
}
