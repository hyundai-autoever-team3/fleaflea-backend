package com.anabada.fleaflea.domain.itemcomment.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ItemCommentNotFoundException extends BusinessException {

    public ItemCommentNotFoundException() {
        super(ErrorCode.ITEM_COMMENT_NOT_FOUND);
    }
}
