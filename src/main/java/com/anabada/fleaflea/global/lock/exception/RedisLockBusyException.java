package com.anabada.fleaflea.global.lock.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class RedisLockBusyException extends BusinessException {

    public RedisLockBusyException() {
        super(ErrorCode.RESOURCE_BUSY);
    }
}
