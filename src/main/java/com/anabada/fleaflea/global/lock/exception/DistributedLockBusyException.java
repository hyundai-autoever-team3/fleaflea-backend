package com.anabada.fleaflea.global.lock.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class DistributedLockBusyException extends BusinessException {

    public DistributedLockBusyException() {
        super(ErrorCode.RESOURCE_BUSY);
    }
}
