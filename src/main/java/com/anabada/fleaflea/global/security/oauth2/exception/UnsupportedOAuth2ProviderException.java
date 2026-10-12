package com.anabada.fleaflea.global.security.oauth2.exception;

import com.anabada.fleaflea.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class UnsupportedOAuth2ProviderException
        extends OAuth2LoginException {

    private final String registrationId;

    public UnsupportedOAuth2ProviderException(
            String registrationId
    ) {
        super(ErrorCode.UNSUPPORTED_OAUTH2_PROVIDER);
        this.registrationId = registrationId;
    }
}