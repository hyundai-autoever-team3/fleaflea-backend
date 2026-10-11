package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeInvalidStatusException extends BusinessException {

    public CollectionTradeInvalidStatusException() {
        super(ErrorCode.COLLECTION_TRADE_INVALID_STATUS);
    }
}
