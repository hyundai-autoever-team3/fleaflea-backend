package com.anabada.fleaflea.global.security.oauth2.exception;

import com.anabada.fleaflea.global.exception.ErrorCode;

public class OAuth2UserInfoFetchFailedException
        extends OAuth2LoginException {

    public OAuth2UserInfoFetchFailedException(Throwable cause) {
        super(ErrorCode.OAUTH2_USER_INFO_FETCH_FAILED, cause);
    }
}