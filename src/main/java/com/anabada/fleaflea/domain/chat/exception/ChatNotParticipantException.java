package com.anabada.fleaflea.domain.chat.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ChatNotParticipantException extends BusinessException {

    public ChatNotParticipantException() {
        super(ErrorCode.CHAT_NOT_PARTICIPANT);
    }
}
