package com.anabada.fleaflea.domain.market.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MarketAccessDeniedException extends BusinessException {

    public MarketAccessDeniedException() {
        super(ErrorCode.MARKET_ACCESS_DENIED);
    }
}