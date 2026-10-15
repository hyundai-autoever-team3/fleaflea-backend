package com.anabada.fleaflea.domain.itemcomment.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemCommentInvalidParentException extends BusinessException {

    public ItemCommentInvalidParentException() {
        super(ErrorCode.ITEM_COMMENT_INVALID_PARENT);
    }
}
