package com.anabada.fleaflea.domain.item.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemImageRequiredException extends BusinessException {

    public ItemImageRequiredException() {
        super(ErrorCode.ITEM_IMAGE_REQUIRED);
    }
}