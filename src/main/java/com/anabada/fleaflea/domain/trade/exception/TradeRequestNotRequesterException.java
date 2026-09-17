package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestNotRequesterException extends BusinessException {

    public TradeRequestNotRequesterException() {
        super(ErrorCode.TRADE_REQUEST_NOT_REQUESTER);
    }
}