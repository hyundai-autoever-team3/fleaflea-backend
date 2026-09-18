package com.anabada.fleaflea.domain.trade.event;

public record TradeRejectedEvent(
        Long requestId,
        Long requesterId,
        Long rejecterId,
        String rejecterNickname,
        TradeTarget target
) {

    public static TradeRejectedEvent of(
            Long requestId,
            Long requesterId,
            Long rejecterId,
            String rejecterNickname,
            TradeTarget target
    ) {
        return new TradeRejectedEvent(
                requestId,
                requesterId,
                rejecterId,
                rejecterNickname,
                target
        );
    }
}