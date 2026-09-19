package com.anabada.fleaflea.domain.trade.event;

public record TradeTarget(
        TradeKind kind,
        TradeDealType dealType,
        Long id,
        String name
) {

    public static TradeTarget of(
            TradeKind kind,
            TradeDealType dealType,
            Long id,
            String name
    ) {
        return new TradeTarget(
                kind,
                dealType,
                id,
                name
        );
    }
}