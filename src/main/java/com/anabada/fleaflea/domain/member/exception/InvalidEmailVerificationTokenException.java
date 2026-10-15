package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidEmailVerificationTokenException extends BusinessException {

    public InvalidEmailVerificationTokenException() {
        super(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN);
    }
}
