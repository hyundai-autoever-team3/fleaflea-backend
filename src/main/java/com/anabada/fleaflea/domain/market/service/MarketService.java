package com.anabada.fleaflea.domain.market.service;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.dto.MarketCreateRequest;
import com.anabada.fleaflea.domain.market.dto.MarketCreateResponse;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
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

    @Transactional
    public MarketCreateResponse createMarket(
            Long memberId,
            MarketCreateRequest request
    ) {
        Member host = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        String inviteCode = generateUniqueInviteCode();

        Market market = Market.create(
                host,
                request.title(),
                request.description(),
                inviteCode
        );

        Market savedMarket = marketRepository.save(market);

        MarketMember hostMembership = MarketMember.create(
                savedMarket,
                host
        );

        marketMemberRepository.save(hostMembership);

        return MarketCreateResponse.from(savedMarket);
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
}