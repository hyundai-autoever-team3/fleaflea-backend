package com.anabada.fleaflea.domain.trade.dto;

import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CollectionTradeRequestResponse(
        Long collectionTradeRequestId,
        Long collectionItemId,
        String collectionItemTitle,
        Long requesterId,
        String requesterNickname,
        Long offerCollectionItemId,
        String offerCollectionItemTitle,
        CollectionTradeType tradeType,
        TradeRequestStatus status,
        LocalDateTime createdAt
) {
    public static CollectionTradeRequestResponse from(CollectionTradeRequest request) {
        return CollectionTradeRequestResponse.builder()
                .collectionTradeRequestId(request.getCollectionTradeRequestId())
                .collectionItemId(request.getCollectionItemSnapshotId())
                .collectionItemTitle(request.getCollectionItemTitle())
                .requesterId(request.getRequester().getMemberId())
                .requesterNickname(request.getRequester().getNickname())
                .offerCollectionItemId(request.getOfferCollectionItemSnapshotId())
                .offerCollectionItemTitle(request.getOfferCollectionItemTitle())
                .tradeType(request.getTradeType())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
