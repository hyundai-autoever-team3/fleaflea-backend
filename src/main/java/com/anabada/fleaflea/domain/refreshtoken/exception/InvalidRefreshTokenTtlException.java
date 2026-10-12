package com.anabada.fleaflea.domain.refreshtoken.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidRefreshTokenTtlException extends BusinessException {

    public InvalidRefreshTokenTtlException() {
        super(ErrorCode.INVALID_REFRESH_TOKEN_TTL);
    }
}
