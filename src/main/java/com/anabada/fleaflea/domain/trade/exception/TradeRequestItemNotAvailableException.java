package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestItemNotAvailableException extends BusinessException {

    public TradeRequestItemNotAvailableException() {
        super(ErrorCode.TRADE_REQUEST_ITEM_NOT_AVAILABLE);
    }
}