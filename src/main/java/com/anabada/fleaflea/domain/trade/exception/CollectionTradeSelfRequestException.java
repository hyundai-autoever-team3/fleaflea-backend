package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionTradeSelfRequestException extends BusinessException {

    public CollectionTradeSelfRequestException() {
        super(ErrorCode.COLLECTION_TRADE_SELF_REQUEST);
    }
}
