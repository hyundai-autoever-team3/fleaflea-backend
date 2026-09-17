package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestSelfItemException extends BusinessException {
    public TradeRequestSelfItemException() { super(ErrorCode.TRADE_REQUEST_SELF_ITEM); }
}
