package com.anabada.fleaflea.domain.item.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemNotOwnerException extends BusinessException {

    public ItemNotOwnerException() {
        super(ErrorCode.ITEM_NOT_OWNER);
    }
}
