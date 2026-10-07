package com.anabada.fleaflea.global.lock.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class RedisLockUnavailableException extends BusinessException {

    public RedisLockUnavailableException(Throwable cause) {
        super(ErrorCode.REDIS_LOCK_UNAVAILABLE, cause);
    }
}
