package com.anabada.fleaflea.domain.trade.dto;

import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "지난 거래 요청 통합 상세")
public record TradeRequestHistoryDetailResponse(
        String requestType,
        Long requestId,
        Long targetItemId,
        String targetItemTitle,
        String targetItemDescription,
        String targetItemImageUrl,
        String tradeType,
        Long price,
        TradeRequestStatus status,
        MemberSummaryResponse owner,
        MemberSummaryResponse requester,
        OfferItem offerItem,
        String message,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
    public record OfferItem(
            Long collectionItemId,
            String title,
            String description,
            String imageUrl
    ) {
    }
}
