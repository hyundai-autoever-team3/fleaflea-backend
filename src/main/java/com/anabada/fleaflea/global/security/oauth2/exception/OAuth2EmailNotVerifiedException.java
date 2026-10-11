package com.anabada.fleaflea.global.security.oauth2.exception;

import com.anabada.fleaflea.global.exception.ErrorCode;

public class OAuth2EmailNotVerifiedException
        extends OAuth2LoginException {

    public OAuth2EmailNotVerifiedException() {
        super(ErrorCode.OAUTH2_EMAIL_NOT_VERIFIED);
    }
}