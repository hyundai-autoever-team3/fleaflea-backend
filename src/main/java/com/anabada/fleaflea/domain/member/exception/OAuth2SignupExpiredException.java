package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class OAuth2SignupExpiredException
        extends BusinessException {

    public OAuth2SignupExpiredException() {
        super(ErrorCode.OAUTH2_SIGNUP_EXPIRED);
    }
}