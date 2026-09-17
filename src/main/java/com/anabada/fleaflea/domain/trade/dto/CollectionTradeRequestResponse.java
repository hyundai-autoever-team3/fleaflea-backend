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
        Long offerItemId = request.getOfferCollectionItem() != null ? 
                request.getOfferCollectionItem().getCollectionItemId() : null;
        String offerItemTitle = request.getOfferCollectionItem() != null ? 
                request.getOfferCollectionItem().getTitle() : null;

        return CollectionTradeRequestResponse.builder()
                .collectionTradeRequestId(request.getCollectionTradeRequestId())
                .collectionItemId(request.getCollectionItem().getCollectionItemId())
                .collectionItemTitle(request.getCollectionItem().getTitle())
                .requesterId(request.getRequester().getMemberId())
                .requesterNickname(request.getRequester().getNickname())
                .offerCollectionItemId(offerItemId)
                .offerCollectionItemTitle(offerItemTitle)
                .tradeType(request.getTradeType())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
