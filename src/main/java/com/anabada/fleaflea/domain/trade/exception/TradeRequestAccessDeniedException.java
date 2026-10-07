package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestAccessDeniedException extends BusinessException {

    public TradeRequestAccessDeniedException() {
        super(ErrorCode.TRADE_REQUEST_ACCESS_DENIED);
    }
}
