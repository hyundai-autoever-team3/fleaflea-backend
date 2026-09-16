package com.anabada.fleaflea.domain.item.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemAlreadyCompletedException extends BusinessException {

    public ItemAlreadyCompletedException() {
        super(ErrorCode.ITEM_ALREADY_COMPLETED);
    }
}
