package com.anabada.fleaflea.domain.marketmember.repository;

import com.anabada.fleaflea.domain.market.dto.MarketSearchCondition;
import com.anabada.fleaflea.domain.market.dto.MarketSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MarketMemberRepositoryCustom {

    Page<MarketSummaryProjection> searchJoinedMarkets(
            Long memberId,
            MarketSearchCondition condition,
            Pageable pageable
    );
}
