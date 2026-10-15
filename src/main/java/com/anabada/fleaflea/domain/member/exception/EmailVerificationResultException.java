package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class EmailVerificationResultException extends BusinessException {

    public EmailVerificationResultException() {
        super(ErrorCode.EMAIL_VERIFICATION_RESULT_INVALID);
    }
}
