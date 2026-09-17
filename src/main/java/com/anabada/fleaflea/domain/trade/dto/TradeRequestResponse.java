package com.anabada.fleaflea.domain.trade.dto;

import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record TradeRequestResponse(
        Long tradeRequestId,
        Long itemId,
        String itemTitle,
        Long requesterId,
        String requesterNickname,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        TradeRequestStatus status,
        LocalDateTime createdAt
) {
    public static TradeRequestResponse from(TradeRequest request) {
        return TradeRequestResponse.builder()
                .tradeRequestId(request.getTradeRequestId())
                .itemId(request.getItem().getItemId())
                .itemTitle(request.getItem().getTitle())
                .requesterId(request.getRequester().getMemberId())
                .requesterNickname(request.getRequester().getNickname())
                .rentalStartDate(request.getRentalStartDate())
                .rentalEndDate(request.getRentalEndDate())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
