package com.anabada.fleaflea.domain.trade.event;

public record TradeCompletedEvent(
        Long requestId,
        Long confirmerId,
        Long counterpartyId,
        String confirmerNickname,
        TradeTarget target
) {

    public static TradeCompletedEvent of(
            Long requestId,
            Long confirmerId,
            Long counterpartyId,
            String confirmerNickname,
            TradeTarget target
    ) {
        return new TradeCompletedEvent(
                requestId,
                confirmerId,
                counterpartyId,
                confirmerNickname,
                target
        );
    }
}