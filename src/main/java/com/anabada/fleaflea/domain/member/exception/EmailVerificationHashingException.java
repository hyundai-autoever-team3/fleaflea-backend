package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class EmailVerificationHashingException extends BusinessException {

    public EmailVerificationHashingException() {
        super(ErrorCode.EMAIL_VERIFICATION_HASHING_FAILED);
    }

    public EmailVerificationHashingException(Throwable cause) {
        super(ErrorCode.EMAIL_VERIFICATION_HASHING_FAILED, cause);
    }
}
