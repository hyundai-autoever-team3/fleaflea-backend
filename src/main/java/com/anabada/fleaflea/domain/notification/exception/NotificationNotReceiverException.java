package com.anabada.fleaflea.domain.notification.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class NotificationNotReceiverException extends BusinessException {

    public NotificationNotReceiverException() {
        super(ErrorCode.NOTIFICATION_NOT_RECEIVER);
    }
}