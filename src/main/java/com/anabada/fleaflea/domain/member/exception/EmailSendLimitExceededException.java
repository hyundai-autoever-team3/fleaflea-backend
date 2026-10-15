package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class EmailSendLimitExceededException extends BusinessException {

    public EmailSendLimitExceededException() {
        super(ErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);
    }
}