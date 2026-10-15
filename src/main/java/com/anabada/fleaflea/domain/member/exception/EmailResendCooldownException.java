package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class EmailResendCooldownException extends BusinessException {

    public EmailResendCooldownException() {
        super(ErrorCode.EMAIL_RESEND_COOLDOWN);
    }
}