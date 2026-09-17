package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestSelfRequestException extends BusinessException {

    public TradeRequestSelfRequestException() {
        super(ErrorCode.TRADE_REQUEST_SELF_REQUEST);
    }
}