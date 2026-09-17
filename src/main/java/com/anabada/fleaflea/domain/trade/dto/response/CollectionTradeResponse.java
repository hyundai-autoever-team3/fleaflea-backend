package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CollectionTradeResponse(

        @Schema(description = "거래 요청 ID")
        Long requestId,

        @Schema(description = "거래 대상 도감 아이템 ID")
        Long targetCollectionItemId,

        @Schema(description = "거래 대상 아이템 소유자")
        MemberSummaryResponse owner,

        @Schema(description = "거래 요청자")
        MemberSummaryResponse requester,

        @Schema(description = "교환 제안 아이템 ID. 대여 요청이면 null")
        Long offerCollectionItemId,

        @Schema(description = "거래 방식")
        CollectionTradeType tradeType,

        @Schema(description = "거래 요청 상태")
        TradeRequestStatus status,

        @Schema(description = "요청자의 거래 완료 확인 여부")
        boolean requesterConfirmed,

        @Schema(description = "소유자의 거래 완료 확인 여부")
        boolean ownerConfirmed,

        @Schema(description = "요청 생성 시각")
        LocalDateTime createdAt,

        @Schema(description = "요청 수정 시각")
        LocalDateTime updatedAt
) {

    public static CollectionTradeResponse from(
            CollectionTradeRequest request,
            MemberSummaryResponse owner,
            MemberSummaryResponse requester
    ) {
        Long offerItemId = request.getOfferItem() == null
                ? null
                : request.getOfferItem().getCollectionItemId();

        return new CollectionTradeResponse(
                request.getCollectionTradeRequestId(),
                request.getTargetItem().getCollectionItemId(),
                owner,
                requester,
                offerItemId,
                request.getTradeType(),
                request.getStatus(),
                request.isRequesterConfirmed(),
                request.isOwnerConfirmed(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}