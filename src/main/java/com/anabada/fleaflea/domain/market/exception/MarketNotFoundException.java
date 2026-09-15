package com.anabada.fleaflea.domain.market.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MarketNotFoundException extends BusinessException {

    public MarketNotFoundException() {
        super(ErrorCode.MARKET_NOT_FOUND);
    }
}