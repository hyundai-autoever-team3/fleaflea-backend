package com.anabada.fleaflea.global.security.oauth2.exception;

import com.anabada.fleaflea.global.exception.ErrorCode;

public class UnsupportedOAuth2PrincipalException
        extends OAuth2LoginException {

    public UnsupportedOAuth2PrincipalException() {
        super(ErrorCode.OAUTH2_PRINCIPAL_UNSUPPORTED);
    }
}