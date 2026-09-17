package com.anabada.fleaflea.domain.trade.dto.request;

import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CollectionTradeCreateRequest(

        @NotNull(message = "거래 방식은 필수입니다.")
        @Schema(description = "거래 방식", example = "EXCHANGE")
        CollectionTradeType tradeType,

        @Schema(
                description = "교환으로 제공할 도감 아이템 ID",
                example = "2"
        )
        Long offerCollectionItemId
) {
}