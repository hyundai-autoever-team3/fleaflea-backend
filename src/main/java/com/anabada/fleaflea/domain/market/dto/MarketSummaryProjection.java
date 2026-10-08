package com.anabada.fleaflea.domain.market.dto;

import com.querydsl.core.annotations.QueryProjection;

import java.time.LocalDateTime;

public record MarketSummaryProjection(
        Long marketId,
        Long hostId,
        String hostNickname,
        String title,
        String description,
        String coverImageKey,
        LocalDateTime joinedAt
) {

    @QueryProjection
    public MarketSummaryProjection {
    }
}
