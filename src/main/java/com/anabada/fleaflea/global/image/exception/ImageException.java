package com.anabada.fleaflea.global.image.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ImageException extends BusinessException {

    public ImageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ImageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}