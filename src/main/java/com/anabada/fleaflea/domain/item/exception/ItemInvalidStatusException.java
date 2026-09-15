package com.anabada.fleaflea.domain.item.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemInvalidStatusException extends BusinessException {

    public ItemInvalidStatusException() {
        super(ErrorCode.INVALID_REQUEST);
    }
}
