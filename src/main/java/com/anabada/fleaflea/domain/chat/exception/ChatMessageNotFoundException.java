package com.anabada.fleaflea.domain.chat.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ChatMessageNotFoundException extends BusinessException {

    public ChatMessageNotFoundException() {
        super(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }
}
