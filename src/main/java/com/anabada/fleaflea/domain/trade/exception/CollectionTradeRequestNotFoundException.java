package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeRequestNotFoundException extends BusinessException {

    public CollectionTradeRequestNotFoundException() {
        super(ErrorCode.COLLECTION_TRADE_REQUEST_NOT_FOUND);
    }
}
