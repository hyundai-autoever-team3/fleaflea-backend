package com.anabada.fleaflea.domain.chat.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ChatFriendRequiredException extends BusinessException {

    public ChatFriendRequiredException() {
        super(ErrorCode.CHAT_FRIEND_REQUIRED);
    }
}
