package com.anabada.fleaflea.domain.item.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemTradeNotInProgressException extends BusinessException {

    public ItemTradeNotInProgressException() {
        super(ErrorCode.ITEM_TRADE_NOT_IN_PROGRESS);
    }
}
