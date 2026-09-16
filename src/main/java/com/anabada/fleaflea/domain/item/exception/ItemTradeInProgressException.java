package com.anabada.fleaflea.domain.item.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemTradeInProgressException extends BusinessException {

    public ItemTradeInProgressException() {
        super(ErrorCode.ITEM_TRADE_IN_PROGRESS);
    }
}
