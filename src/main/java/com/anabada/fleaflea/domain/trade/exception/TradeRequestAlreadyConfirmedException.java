package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestAlreadyConfirmedException extends BusinessException {

    public TradeRequestAlreadyConfirmedException() {
        super(ErrorCode.TRADE_REQUEST_ALREADY_CONFIRMED);
    }
}