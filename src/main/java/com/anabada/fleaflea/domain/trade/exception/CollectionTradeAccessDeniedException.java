package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeAccessDeniedException extends BusinessException {

    public CollectionTradeAccessDeniedException() {
        super(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
    }
}
