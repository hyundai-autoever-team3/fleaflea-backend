package com.anabada.fleaflea.domain.chat.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ChatRateLimitExceededException extends BusinessException {

    public ChatRateLimitExceededException() {
        super(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
    }
}
