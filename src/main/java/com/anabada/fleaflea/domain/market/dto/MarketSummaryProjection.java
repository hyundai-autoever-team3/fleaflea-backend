package com.anabada.fleaflea.domain.market.dto;

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
}