package com.anabada.fleaflea.domain.market.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MarketHostCannotLeaveException extends BusinessException {

    public MarketHostCannotLeaveException() {
        super(ErrorCode.MARKET_HOST_CANNOT_LEAVE);
    }
}
