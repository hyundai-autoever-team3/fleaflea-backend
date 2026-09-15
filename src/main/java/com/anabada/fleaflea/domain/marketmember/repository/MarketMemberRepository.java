package com.anabada.fleaflea.domain.marketmember.repository;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketMemberRepository extends JpaRepository<MarketMember, Long> {

    boolean existsByMarketAndMember(Market market, Member member);

    List<MarketMember> findAllByMember(Member member);

    List<MarketMember> findAllByMarket(Market market);

    long countByMarket(Market market);

    boolean existsByMarket_MarketIdAndMember_MemberId(Long marketId, Long memberId);
}
