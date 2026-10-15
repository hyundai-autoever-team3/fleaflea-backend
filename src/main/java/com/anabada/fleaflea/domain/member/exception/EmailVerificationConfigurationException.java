package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class EmailVerificationConfigurationException extends BusinessException {

    public EmailVerificationConfigurationException() {
        super(ErrorCode.EMAIL_VERIFICATION_CONFIGURATION_INVALID);
    }
}
