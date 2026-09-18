package com.anabada.fleaflea.domain.trade.event;

public record TradeAcceptedEvent(
        Long requestId,
        Long requesterId,
        Long accepterId,
        String accepterNickname,
        TradeTarget target
) {

    public static TradeAcceptedEvent of(
            Long requestId,
            Long requesterId,
            Long accepterId,
            String accepterNickname,
            TradeTarget target
    ) {
        return new TradeAcceptedEvent(
                requestId,
                requesterId,
                accepterId,
                accepterNickname,
                target
        );
    }
}