package com.anabada.fleaflea.domain.market.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidMarketScopeException extends BusinessException {

    public InvalidMarketScopeException() {
        super(ErrorCode.INVALID_REQUEST);
    }
}
