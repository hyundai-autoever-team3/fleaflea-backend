package com.anabada.fleaflea.domain.market.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MarketHostOnlyException extends BusinessException {

    public MarketHostOnlyException() {
        super(ErrorCode.MARKET_HOST_ONLY);
    }
}
