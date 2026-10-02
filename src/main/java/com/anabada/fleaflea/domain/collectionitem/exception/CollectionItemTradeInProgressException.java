package com.anabada.fleaflea.domain.collectionitem.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionItemTradeInProgressException extends BusinessException {

    public CollectionItemTradeInProgressException() {
        super(ErrorCode.COLLECTION_ITEM_TRADE_IN_PROGRESS);
    }
}
