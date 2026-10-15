package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class SocialAccountPasswordResetNotAllowedException extends BusinessException {

    public SocialAccountPasswordResetNotAllowedException() {
        super(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_RESET_NOT_ALLOWED);
    }
}
