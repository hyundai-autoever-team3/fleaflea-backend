package com.anabada.fleaflea.domain.trade.event;

public record TradeTarget(
        TradeKind kind,
        Long id,
        String name
) {

    public static TradeTarget of(
            TradeKind kind,
            Long id,
            String name
    ) {
        return new TradeTarget(
                kind,
                id,
                name
        );
    }
}