package com.anabada.fleaflea.domain.item.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemNotAvailableException extends BusinessException {

    public ItemNotAvailableException() {
        super(ErrorCode.ITEM_NOT_AVAILABLE);
    }
}
