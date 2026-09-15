package com.anabada.fleaflea.domain.collectionitem.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionItemNotFoundException
        extends BusinessException {

    public CollectionItemNotFoundException() {
        super(ErrorCode.COLLECTION_ITEM_NOT_FOUND);
    }
}