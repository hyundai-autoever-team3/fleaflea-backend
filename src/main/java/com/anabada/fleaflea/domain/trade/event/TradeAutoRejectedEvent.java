package com.anabada.fleaflea.domain.trade.event;

public record TradeAutoRejectedEvent(
        Long requestId,
        Long requesterId,
        Long sellerId,
        String sellerNickname,
        TradeTarget target
) {

    public static TradeAutoRejectedEvent of(
            Long requestId,
            Long requesterId,
            Long sellerId,
            String sellerNickname,
            TradeTarget target
    ) {
        return new TradeAutoRejectedEvent(
                requestId,
                requesterId,
                sellerId,
                sellerNickname,
                target
        );
    }
}
