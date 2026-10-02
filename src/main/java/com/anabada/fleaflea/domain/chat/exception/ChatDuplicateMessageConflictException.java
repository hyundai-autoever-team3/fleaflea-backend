package com.anabada.fleaflea.domain.chat.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ChatDuplicateMessageConflictException extends BusinessException {

    public ChatDuplicateMessageConflictException() {
        super(ErrorCode.CHAT_DUPLICATE_MESSAGE_CONFLICT);
    }
}
