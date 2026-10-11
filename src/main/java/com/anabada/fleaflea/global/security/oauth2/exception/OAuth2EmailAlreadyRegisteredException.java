package com.anabada.fleaflea.global.security.oauth2.exception;

import com.anabada.fleaflea.global.exception.ErrorCode;

public class OAuth2EmailAlreadyRegisteredException
        extends OAuth2LoginException {

    public OAuth2EmailAlreadyRegisteredException() {
        super(ErrorCode.DUPLICATE_EMAIL);
    }

    public OAuth2EmailAlreadyRegisteredException(
            Throwable cause
    ) {
        super(ErrorCode.DUPLICATE_EMAIL, cause);
    }
}