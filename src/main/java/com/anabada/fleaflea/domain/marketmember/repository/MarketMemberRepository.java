package com.anabada.fleaflea.domain.marketmember.repository;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.dto.MarketSummaryProjection;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MarketMemberRepository extends JpaRepository<MarketMember, Long> {

    boolean existsByMarketAndMember(Market market, Member member);

    @EntityGraph(attributePaths = {"market", "market.host"})
    Page<MarketMember> findAllByMember(Member member, Pageable pageable);

    @Query("""
            select new com.anabada.fleaflea.domain.market.dto.MarketSummaryProjection(
                market.marketId,
                host.memberId,
                host.nickname,
                market.title,
                market.description,
                market.coverImageKey,
                marketMember.joinedAt
            )
            from MarketMember marketMember
            join marketMember.market market
            join market.host host
            where marketMember.member.memberId = :memberId
            """)
    Page<MarketSummaryProjection> findMarketSummariesByMemberId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    long countByMarket(Market market);

    boolean existsByMarket_MarketIdAndMember_MemberId(Long marketId, Long memberId);

    @EntityGraph(attributePaths = {"member"})
    Page<MarketMember> findAllByMarket(Market market, Pageable pageable);

    Optional<MarketMember> findByMarketAndMember(
            Market market,
            Member member
    );

    void deleteAllByMarket(Market market);
}