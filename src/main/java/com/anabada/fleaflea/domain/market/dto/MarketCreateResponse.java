package com.anabada.fleaflea.domain.market.dto;

import com.anabada.fleaflea.domain.market.domain.Market;

public record MarketCreateResponse(
        Long marketId,
        Long hostId,
        String title,
        String description,
        String inviteCode
) {

    public static MarketCreateResponse from(Market market) {
        return new MarketCreateResponse(
                market.getMarketId(),
                market.getHost().getMemberId(),
                market.getTitle(),
                market.getDescription(),
                market.getInviteCode()
        );
    }
}