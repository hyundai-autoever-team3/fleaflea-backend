package com.anabada.fleaflea.domain.trade.dto;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "지난 거래 요청 통합 상세")
public record TradeRequestHistoryDetailResponse(
        @Schema(description = "거래 요청 유형: ITEM, COLLECTION 또는 BEG")
        String requestType,

        @Schema(description = "거래 요청 ID")
        Long requestId,

        @Schema(description = "거래 대상 물건 ID")
        Long targetItemId,

        @Schema(description = "거래 대상 물건 이름")
        String targetItemTitle,

        @Schema(description = "거래 대상 물건 설명")
        String targetItemDescription,

        @Schema(description = "거래 대상 물건 이미지 URL")
        String targetItemImageUrl,

        @Schema(description = "거래 방식")
        String tradeType,

        @Schema(description = "거래 가격")
        Long price,

        @Schema(description = "거래 요청 상태")
        TradeRequestStatus status,

        @Schema(description = "물건 소유자")
        MemberSummaryResponse owner,

        @Schema(description = "거래 요청자")
        MemberSummaryResponse requester,

        @Schema(description = "교환 제안 물건")
        OfferItem offerItem,

        @Schema(description = "거래 요청 내용")
        String message,

        @Schema(description = "대여 시작일")
        LocalDate rentalStartDate,

        @Schema(description = "대여 종료일")
        LocalDate rentalEndDate,

        @Schema(description = "거래 요청 생성 시각")
        LocalDateTime createdAt,

        @Schema(description = "거래 요청 수정 시각")
        LocalDateTime updatedAt,

        @Schema(description = "거래 완료 시각")
        LocalDateTime completedAt
) {

    public static TradeRequestHistoryDetailResponse from(
            TradeRequest tradeRequest,
            MemberSummaryResponse owner,
            MemberSummaryResponse requester,
            String targetItemImageUrl,
            LocalDateTime completedAt
    ) {
        Item item = tradeRequest.getItem();

        return new TradeRequestHistoryDetailResponse(
                "ITEM",
                tradeRequest.getTradeRequestId(),
                item.getItemId(),
                item.getTitle(),
                item.getDescription(),
                targetItemImageUrl,
                item.getTradeType().name(),
                item.getPrice(),
                tradeRequest.getStatus(),
                owner,
                requester,
                null,
                tradeRequest.getMessage(),
                tradeRequest.getRentalStartDate(),
                tradeRequest.getRentalEndDate(),
                tradeRequest.getCreatedAt(),
                tradeRequest.getUpdatedAt(),
                completedAt
        );
    }

    public static TradeRequestHistoryDetailResponse from(
            CollectionTradeRequest collectionTradeRequest,
            MemberSummaryResponse owner,
            MemberSummaryResponse requester,
            String targetItemImageUrl,
            OfferItem offerItem,
            LocalDateTime completedAt
    ) {
        return new TradeRequestHistoryDetailResponse(
                "COLLECTION",
                collectionTradeRequest.getCollectionTradeRequestId(),
                collectionTradeRequest.getCollectionItemSnapshotId(),
                collectionTradeRequest.getCollectionItemTitle(),
                collectionTradeRequest.getCollectionItemDescription(),
                targetItemImageUrl,
                collectionTradeRequest.getTradeType().name(),
                null,
                collectionTradeRequest.getStatus(),
                owner,
                requester,
                offerItem,
                null,
                null,
                null,
                collectionTradeRequest.getCreatedAt(),
                collectionTradeRequest.getUpdatedAt(),
                completedAt
        );
    }

    public static TradeRequestHistoryDetailResponse from(
            BegRequest begRequest,
            MemberSummaryResponse owner,
            MemberSummaryResponse requester,
            String targetItemImageUrl,
            LocalDateTime completedAt
    ) {
        return new TradeRequestHistoryDetailResponse(
                "BEG",
                begRequest.getBegRequestId(),
                begRequest.getCollectionItemSnapshotId(),
                begRequest.getCollectionItemTitle(),
                begRequest.getCollectionItemDescription(),
                targetItemImageUrl,
                null,
                null,
                begRequest.getStatus().toTradeRequestStatus(),
                owner,
                requester,
                null,
                begRequest.getStory(),
                null,
                null,
                begRequest.getCreatedAt(),
                begRequest.getUpdatedAt(),
                completedAt
        );
    }

    public record OfferItem(
            @Schema(description = "교환 제안 도감 아이템 ID")
            Long collectionItemId,

            @Schema(description = "교환 제안 물건 이름")
            String title,

            @Schema(description = "교환 제안 물건 설명")
            String description,

            @Schema(description = "교환 제안 물건 이미지 URL")
            String imageUrl
    ) {

        public static OfferItem from(
                CollectionTradeRequest collectionTradeRequest,
                String imageUrl
        ) {
            if (collectionTradeRequest.getOfferCollectionItemSnapshotId() == null) {
                return null;
            }

            return new OfferItem(
                    collectionTradeRequest.getOfferCollectionItemSnapshotId(),
                    collectionTradeRequest.getOfferCollectionItemTitle(),
                    collectionTradeRequest.getOfferCollectionItemDescription(),
                    imageUrl
            );
        }
    }
}
