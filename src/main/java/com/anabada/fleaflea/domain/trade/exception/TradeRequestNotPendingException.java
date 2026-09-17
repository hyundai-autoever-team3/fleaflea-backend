package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestNotPendingException extends BusinessException {

    public TradeRequestNotPendingException() {
        super(ErrorCode.TRADE_REQUEST_NOT_PENDING);
    }
}