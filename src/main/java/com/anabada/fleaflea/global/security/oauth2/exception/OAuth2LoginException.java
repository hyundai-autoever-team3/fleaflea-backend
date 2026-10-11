package com.anabada.fleaflea.global.security.oauth2.exception;

import com.anabada.fleaflea.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@Getter
public abstract class OAuth2LoginException
        extends OAuth2AuthenticationException {

    private final ErrorCode errorCode;

    protected OAuth2LoginException(ErrorCode errorCode) {
        super(
                new OAuth2Error(errorCode.getCode()),
                errorCode.getMessage()
        );
        this.errorCode = errorCode;
    }

    protected OAuth2LoginException(
            ErrorCode errorCode,
            Throwable cause
    ) {
        super(
                new OAuth2Error(errorCode.getCode()),
                errorCode.getMessage(),
                cause
        );
        this.errorCode = errorCode;
    }
}