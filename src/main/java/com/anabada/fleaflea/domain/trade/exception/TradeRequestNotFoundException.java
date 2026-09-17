package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestNotFoundException extends BusinessException {

    public TradeRequestNotFoundException() {
        super(ErrorCode.TRADE_REQUEST_NOT_FOUND);
    }
}