package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestNotSellerException extends BusinessException {

    public TradeRequestNotSellerException() {
        super(ErrorCode.TRADE_REQUEST_NOT_SELLER);
    }
}