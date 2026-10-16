package com.anabada.fleaflea.domain.notification.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class PushDeviceNotOwnerException extends BusinessException {

    public PushDeviceNotOwnerException() {
        super(ErrorCode.PUSH_DEVICE_NOT_OWNER);
    }
}
