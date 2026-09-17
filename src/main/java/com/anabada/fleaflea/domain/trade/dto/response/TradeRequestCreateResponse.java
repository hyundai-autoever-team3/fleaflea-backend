package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 요청 생성 응답")
public record TradeRequestCreateResponse(
        @Schema(description = "생성된 거래 요청 ID", example = "15")
        Long tradeRequestId
) {

    public static TradeRequestCreateResponse from(TradeRequest tradeRequest) {
        return new TradeRequestCreateResponse(
                tradeRequest.getTradeRequestId()
        );
    }
}
