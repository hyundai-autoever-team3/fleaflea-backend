package com.anabada.fleaflea.domain.marketmember.dto;

import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;

import java.time.LocalDateTime;

public record MarketJoinResponse(
        Long marketId,
        String title,
        LocalDateTime joinedAt
) {

    public static MarketJoinResponse from(MarketMember marketMember) {
        return new MarketJoinResponse(
                marketMember.getMarket().getMarketId(),
                marketMember.getMarket().getTitle(),
                marketMember.getJoinedAt()
        );
    }
}