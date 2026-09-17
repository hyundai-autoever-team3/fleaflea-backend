package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 요청 상태 변경 응답")
public record TradeRequestStatusResponse(
        @Schema(description = "거래 요청 ID", example = "1")
        Long tradeRequestId,

        @Schema(
                description = "변경된 거래 요청 상태. "
                        + "PENDING: 수락 대기, "
                        + "ACCEPTED: 거래 진행 중, "
                        + "REJECTED: 요청 거절, "
                        + "CANCELLED: 요청 취소, "
                        + "COMPLETED: 거래 완료",
                example = "ACCEPTED"
        )
        TradeRequestStatus status
) {
    public static TradeRequestStatusResponse from(TradeRequest tradeRequest) {
        return new TradeRequestStatusResponse(
                tradeRequest.getTradeRequestId(),
                tradeRequest.getStatus()
        );
    }
}
