package com.anabada.fleaflea.domain.trade.domain;

public enum TradeRequestDirection {
    SENT,
    RECEIVED;

    public static TradeRequestDirection from(String value) {
        try {
            return TradeRequestDirection.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("올바르지 않은 요청 방향입니다: " + value);
        }
    }
}