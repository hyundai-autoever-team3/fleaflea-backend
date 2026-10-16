package com.anabada.fleaflea.domain.notification.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class PushDeviceRegistrationRateLimitExceededException extends BusinessException {

    public PushDeviceRegistrationRateLimitExceededException() {
        super(ErrorCode.PUSH_DEVICE_REGISTRATION_RATE_LIMIT_EXCEEDED);
    }
}
