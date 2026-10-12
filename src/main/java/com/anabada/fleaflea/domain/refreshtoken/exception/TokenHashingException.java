package com.anabada.fleaflea.domain.refreshtoken.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TokenHashingException extends BusinessException {

    public TokenHashingException(Throwable cause) {
        super(ErrorCode.TOKEN_HASHING_FAILED, cause);
    }
}
