package com.anabada.fleaflea.domain.friendship.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class FriendshipAlreadyExistsException extends BusinessException {

    public FriendshipAlreadyExistsException() {
        super(ErrorCode.FRIENDSHIP_ALREADY_EXISTS);
    }
}