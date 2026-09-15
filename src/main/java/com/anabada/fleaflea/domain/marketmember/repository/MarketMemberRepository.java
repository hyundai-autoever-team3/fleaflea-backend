package com.anabada.fleaflea.domain.marketmember.repository;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMemberRepository extends JpaRepository<MarketMember, Long> {

    boolean existsByMarketAndMember(Market market, Member member);

    @EntityGraph(attributePaths = {"market", "market.host"})
    Page<MarketMember> findAllByMember(Member member, Pageable pageable);

    long countByMarket(Market market);

    @EntityGraph(attributePaths = {"member"})
    Page<MarketMember> findAllByMarket(Market market, Pageable pageable);
}
