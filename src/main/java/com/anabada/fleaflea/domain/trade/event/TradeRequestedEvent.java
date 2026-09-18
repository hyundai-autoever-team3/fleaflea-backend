package com.anabada.fleaflea.domain.trade.event;

public record TradeRequestedEvent(
        Long requestId,
        Long requesterId,
        Long counterpartyId,
        String requesterNickname,
        TradeTarget target
) {

    public static TradeRequestedEvent of(
            Long requestId,
            Long requesterId,
            Long counterpartyId,
            String requesterNickname,
            TradeTarget target
    ) {
        return new TradeRequestedEvent(
                requestId,
                requesterId,
                counterpartyId,
                requesterNickname,
                target
        );
    }
}