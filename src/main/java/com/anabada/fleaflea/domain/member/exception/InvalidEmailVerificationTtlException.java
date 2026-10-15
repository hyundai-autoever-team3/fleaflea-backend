package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidEmailVerificationTtlException extends BusinessException {

    public InvalidEmailVerificationTtlException() {
        super(ErrorCode.INVALID_EMAIL_VERIFICATION_TTL);
    }
}
