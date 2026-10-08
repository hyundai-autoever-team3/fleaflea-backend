package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeOwnershipChangedException extends BusinessException {

    public CollectionTradeOwnershipChangedException() {
        super(ErrorCode.COLLECTION_TRADE_OWNERSHIP_CHANGED);
    }
}
