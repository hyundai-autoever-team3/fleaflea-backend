package com.anabada.fleaflea.domain.notification.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class PushDeviceLimitExceededException extends BusinessException {

    public PushDeviceLimitExceededException() {
        super(ErrorCode.PUSH_DEVICE_LIMIT_EXCEEDED);
    }
}
