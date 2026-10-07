package com.anabada.fleaflea.domain.marketmember.service;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.exception.InvalidMarketInviteCodeException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.dto.MarketJoinRequest;
import com.anabada.fleaflea.domain.marketmember.dto.MarketJoinResponse;
import com.anabada.fleaflea.domain.marketmember.exception.AlreadyJoinedMarketException;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.lock.RedisLocked;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketJoinService {

    private final MarketRepository marketRepository;
    private final MarketMemberRepository marketMemberRepository;
    private final MemberRepository memberRepository;

    @RedisLocked(key = "@marketLockKeys.getMarketKeyByInviteCode(#marketJoinRequest.inviteCode())")
    @Transactional
    public MarketJoinResponse joinMarket(
            Long memberId,
            MarketJoinRequest marketJoinRequest
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        String inviteCode = marketJoinRequest.inviteCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        Long marketId = marketRepository.findMarketIdByInviteCode(inviteCode)
                .orElseThrow(InvalidMarketInviteCodeException::new);
        Market market = marketRepository.findLockedById(marketId)
                .orElseThrow(InvalidMarketInviteCodeException::new);

        if (!market.getInviteCode().equals(inviteCode)) {
            throw new InvalidMarketInviteCodeException();
        }

        if (marketMemberRepository.existsByMarketAndMember(market, member)) {
            throw new AlreadyJoinedMarketException();
        }

        MarketMember marketMember = MarketMember.create(market, member);
        MarketMember savedMarketMember =
                marketMemberRepository.save(marketMember);

        return MarketJoinResponse.from(savedMarketMember);
    }
}
