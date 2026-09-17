package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidProfileImageRequestException extends BusinessException {

    public InvalidProfileImageRequestException() {
        super(ErrorCode.INVALID_PROFILE_IMAGE_REQUEST);
    }
}