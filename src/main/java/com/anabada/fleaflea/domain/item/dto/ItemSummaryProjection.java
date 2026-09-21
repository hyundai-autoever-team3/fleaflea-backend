package com.anabada.fleaflea.domain.item.dto;

import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.querydsl.core.annotations.QueryProjection;

import java.time.LocalDateTime;

public record ItemSummaryProjection(
        Long itemId,
        String title,
        ItemTradeType tradeType,
        Long price,
        ItemStatus status,
        String imageKey,
        LocalDateTime createdAt
) {

    @QueryProjection
    public ItemSummaryProjection {
    }
}
