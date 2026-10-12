package com.anabada.fleaflea.global.security.oauth2.exception;

import com.anabada.fleaflea.global.exception.ErrorCode;

public class OAuth2ProviderIdNotFoundException
        extends OAuth2LoginException {

    public OAuth2ProviderIdNotFoundException() {
        super(ErrorCode.OAUTH2_PROVIDER_ID_NOT_FOUND);
    }
}