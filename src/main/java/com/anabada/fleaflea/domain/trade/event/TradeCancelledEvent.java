package com.anabada.fleaflea.domain.trade.event;

public record TradeCancelledEvent(
        Long requestId,
        Long requesterId,
        Long counterpartyId,
        String requesterNickname,
        TradeTarget target
) {

    public static TradeCancelledEvent of(
            Long requestId,
            Long requesterId,
            Long counterpartyId,
            String requesterNickname,
            TradeTarget target
    ) {
        return new TradeCancelledEvent(
                requestId,
                requesterId,
                counterpartyId,
                requesterNickname,
                target
        );
    }
}