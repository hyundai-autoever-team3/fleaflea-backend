package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidTradeRequestTypeException extends BusinessException {

    public InvalidTradeRequestTypeException() {
        super(ErrorCode.TRADE_REQUEST_INVALID_TYPE);
    }
}
