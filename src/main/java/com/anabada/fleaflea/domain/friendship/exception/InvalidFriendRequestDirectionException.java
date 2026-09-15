package com.anabada.fleaflea.domain.friendship.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidFriendRequestDirectionException extends BusinessException {
    public InvalidFriendRequestDirectionException() {
        super(ErrorCode.FRIEND_REQUEST_INVALID_DIRECTION);
    }
}
