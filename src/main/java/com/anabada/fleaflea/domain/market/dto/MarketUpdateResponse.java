package com.anabada.fleaflea.domain.market.dto;

import com.anabada.fleaflea.domain.market.domain.Market;
import io.swagger.v3.oas.annotations.media.Schema;

public record MarketUpdateResponse(

        @Schema(description = "플리마켓 ID")
        Long marketId,

        @Schema(description = "플리마켓 이름")
        String title,

        @Schema(description = "플리마켓 설명")
        String description,

        @Schema(description = "커버 이미지 URL")
        String coverImageUrl
) {

    public static MarketUpdateResponse from(
            Market market,
            String coverImageUrl
    ) {
        return new MarketUpdateResponse(
                market.getMarketId(),
                market.getTitle(),
                market.getDescription(),
                coverImageUrl
        );
    }
}