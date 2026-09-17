package com.anabada.fleaflea.domain.begrequest.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionItemNotPublicException extends BusinessException {
    public CollectionItemNotPublicException() {
        super(ErrorCode.COLLECTION_ITEM_NOT_PUBLIC);
    }
}
