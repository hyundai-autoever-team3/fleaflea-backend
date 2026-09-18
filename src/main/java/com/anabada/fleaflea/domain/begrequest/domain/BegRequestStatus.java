package com.anabada.fleaflea.domain.begrequest.domain;

import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;

public enum BegRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    COMPLETED;

    public TradeRequestStatus toTradeRequestStatus() {
        return TradeRequestStatus.valueOf(name());
    }
}