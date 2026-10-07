package com.anabada.fleaflea.domain.refreshtoken.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class RefreshTokenStorageUnavailableException extends BusinessException {

    public RefreshTokenStorageUnavailableException(Throwable cause) {
        super(ErrorCode.REFRESH_TOKEN_STORAGE_UNAVAILABLE, cause);
    }
}
