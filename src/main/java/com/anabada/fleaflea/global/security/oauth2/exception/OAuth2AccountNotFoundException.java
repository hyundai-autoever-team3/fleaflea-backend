package com.anabada.fleaflea.global.security.oauth2.exception;

import com.anabada.fleaflea.global.exception.ErrorCode;

public class OAuth2AccountNotFoundException
        extends OAuth2LoginException {

    public OAuth2AccountNotFoundException() {
        super(ErrorCode.OAUTH2_ACCOUNT_NOT_FOUND);
    }
}