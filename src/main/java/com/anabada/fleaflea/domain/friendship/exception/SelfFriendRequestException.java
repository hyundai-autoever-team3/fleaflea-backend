package com.anabada.fleaflea.domain.friendship.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class SelfFriendRequestException extends BusinessException {

    public SelfFriendRequestException() {
        super(ErrorCode.SELF_FRIEND_REQUEST);
    }
}