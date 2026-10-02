package com.anabada.fleaflea.domain.chat.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidChatMessageCursorException extends BusinessException {

    public InvalidChatMessageCursorException() {
        super(ErrorCode.INVALID_REQUEST);
    }
}
