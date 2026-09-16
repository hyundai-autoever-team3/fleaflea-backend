package com.anabada.fleaflea.domain.friendship.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class FriendshipNotFoundException extends BusinessException {

    public FriendshipNotFoundException() {
        super(ErrorCode.FRIENDSHIP_NOT_FOUND);
    }
}