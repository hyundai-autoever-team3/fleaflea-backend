package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestNotAcceptedException extends BusinessException {

    public TradeRequestNotAcceptedException() {
        super(ErrorCode.TRADE_REQUEST_NOT_ACCEPTED);
    }
}