package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeInvalidOfferException extends BusinessException {

    public CollectionTradeInvalidOfferException() {
        super(ErrorCode.COLLECTION_TRADE_INVALID_OFFER);
    }
}
