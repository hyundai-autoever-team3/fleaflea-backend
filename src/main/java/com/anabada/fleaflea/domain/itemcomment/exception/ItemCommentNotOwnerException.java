package com.anabada.fleaflea.domain.itemcomment.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemCommentNotOwnerException extends BusinessException {

    public ItemCommentNotOwnerException() {
        super(ErrorCode.ITEM_COMMENT_NOT_OWNER);
    }
}
