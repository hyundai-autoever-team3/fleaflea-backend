package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidTradeRequestDirectionException extends BusinessException {

    public InvalidTradeRequestDirectionException() {
        super(ErrorCode.TRADE_REQUEST_INVALID_DIRECTION);
    }
}
