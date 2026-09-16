package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeException extends BusinessException {

    public CollectionTradeException(ErrorCode errorCode) {
        super(errorCode);
    }
}