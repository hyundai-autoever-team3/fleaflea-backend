package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestAlreadyExistsException extends BusinessException {

    public TradeRequestAlreadyExistsException() {
        super(ErrorCode.TRADE_REQUEST_ALREADY_EXISTS);
    }
}