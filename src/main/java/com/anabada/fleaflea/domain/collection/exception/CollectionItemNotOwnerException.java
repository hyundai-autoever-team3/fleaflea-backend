package com.anabada.fleaflea.domain.collection.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionItemNotOwnerException extends BusinessException {

    public CollectionItemNotOwnerException() {
        super(ErrorCode.COLLECTION_ITEM_NOT_OWNER);
    }
}
