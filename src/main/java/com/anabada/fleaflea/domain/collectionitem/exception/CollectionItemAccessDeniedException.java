package com.anabada.fleaflea.domain.collectionitem.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class CollectionItemAccessDeniedException
        extends BusinessException {

    public CollectionItemAccessDeniedException() {
        super(ErrorCode.COLLECTION_ITEM_ACCESS_DENIED);
    }
}