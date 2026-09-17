package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "거래 요청 목록 항목 응답")
public record TradeRequestSummaryResponse(
        @Schema(description = "거래 요청 ID", example = "1")
        Long tradeRequestId,

        @Schema(
                description = "거래 요청 상태. "
                        + "PENDING: 수락 대기, "
                        + "ACCEPTED: 거래 진행 중, "
                        + "REJECTED: 요청 거절, "
                        + "CANCELLED: 요청 취소, "
                        + "COMPLETED: 거래 완료",
                example = "PENDING"
        )
        TradeRequestStatus tradeRequestStatus,

        @Schema(description = "현재 회원이 거래 요청자인지 여부", example = "false")
        boolean isRequester,

        @Schema(description = "상품 정보")
        TradeRequestItemSummaryResponse item,

        @Schema(description = "거래 상대 회원 정보")
        TradeRequestMemberSummaryResponse counterparty,

        @Schema(description = "거래 요청 생성 일시", example = "2026-09-16T14:30:00")
        LocalDateTime createdAt
) {

    public static TradeRequestSummaryResponse of(
            TradeRequest tradeRequest,
            Long memberId,
            String itemImageUrl
    ) {
        Item item = tradeRequest.getItem();
        boolean isRequester = tradeRequest.getRequester()
                .getMemberId()
                .equals(memberId);
        Member counterparty = isRequester
                ? item.getSeller()
                : tradeRequest.getRequester();

        return new TradeRequestSummaryResponse(
                tradeRequest.getTradeRequestId(),
                tradeRequest.getStatus(),
                isRequester,
                TradeRequestItemSummaryResponse.of(item, itemImageUrl),
                TradeRequestMemberSummaryResponse.from(counterparty),
                tradeRequest.getCreatedAt()
        );
    }
}
