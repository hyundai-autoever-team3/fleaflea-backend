package com.anabada.fleaflea.domain.trade.dto;

import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;

import java.time.LocalDateTime;

public record CollectionTradeResponse(
        Long requestId,
        Long targetCollectionItemId,
        Long targetOwnerId,
        Long requesterId,
        Long offerCollectionItemId,
        CollectionTradeType tradeType,
        TradeRequestStatus status,
        boolean requesterConfirmed,
        boolean ownerConfirmed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CollectionTradeResponse from(
            CollectionTradeRequest request
    ) {
        Long offerItemId = request.getOfferItem() == null
                ? null
                : request.getOfferItem().getCollectionItemId();

        return new CollectionTradeResponse(
                request.getCollectionTradeRequestId(),
                request.getTargetItem().getCollectionItemId(),
                request.getTargetItem().getOwner().getMemberId(),
                request.getRequester().getMemberId(),
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