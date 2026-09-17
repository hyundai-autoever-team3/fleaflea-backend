package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "아이템 거래 요청 상세 응답")
public record TradeRequestDetailResponse(

        @Schema(description = "거래 요청 ID", example = "1")
        Long tradeRequestId,

        @Schema(description = "거래 요청 상태", example = "ACCEPTED")
        TradeRequestStatus status,

        @Schema(description = "현재 회원이 거래 요청자인지 여부", example = "true")
        boolean requestedByMe,

        @Schema(description = "거래 상품 정보")
        TradeItemResponse item,

        @Schema(description = "거래 상대 정보")
        CounterpartyResponse counterparty,

        @Schema(
                description = "거래 요청 메시지",
                example = "다음 동아리 모임 때 받을 수 있을까요?"
        )
        String message,

        @Schema(description = "대여 시작일. 대여가 아니면 null", example = "2026-09-20")
        LocalDate rentalStartDate,

        @Schema(description = "대여 종료일. 대여가 아니면 null", example = "2026-09-27")
        LocalDate rentalEndDate,

        @Schema(description = "거래 요청 생성 일시", example = "2026-09-16T14:30:00")
        LocalDateTime createdAt
) {

    public static TradeRequestDetailResponse of(
            TradeRequest tradeRequest,
            Long memberId,
            String itemImageUrl,
            String counterpartyProfileImageUrl
    ) {
        Item item = tradeRequest.getItem();

        boolean requestedByMe = tradeRequest.getRequester()
                .getMemberId()
                .equals(memberId);

        Member counterparty = requestedByMe
                ? item.getSeller()
                : tradeRequest.getRequester();

        return new TradeRequestDetailResponse(
                tradeRequest.getTradeRequestId(),
                tradeRequest.getStatus(),
                requestedByMe,
                TradeItemResponse.of(item, itemImageUrl),
                CounterpartyResponse.of(
                        counterparty,
                        counterpartyProfileImageUrl
                ),
                tradeRequest.getMessage(),
                tradeRequest.getRentalStartDate(),
                tradeRequest.getRentalEndDate(),
                tradeRequest.getCreatedAt()
        );
    }
}