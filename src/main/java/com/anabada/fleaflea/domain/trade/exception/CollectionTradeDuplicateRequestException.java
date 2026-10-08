package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeDuplicateRequestException extends BusinessException {

    public CollectionTradeDuplicateRequestException() {
        super(ErrorCode.COLLECTION_TRADE_DUPLICATE_REQUEST);
    }
}
