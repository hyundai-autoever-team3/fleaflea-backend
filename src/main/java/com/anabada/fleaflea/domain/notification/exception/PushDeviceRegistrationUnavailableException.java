package com.anabada.fleaflea.domain.notification.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class PushDeviceRegistrationUnavailableException extends BusinessException {

    public PushDeviceRegistrationUnavailableException() {
        super(ErrorCode.PUSH_DEVICE_REGISTRATION_UNAVAILABLE);
    }
}
