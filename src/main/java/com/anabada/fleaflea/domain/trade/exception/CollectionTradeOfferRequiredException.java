package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeOfferRequiredException extends BusinessException {

    public CollectionTradeOfferRequiredException() {
        super(ErrorCode.COLLECTION_TRADE_OFFER_REQUIRED);
    }
}
