package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class ProfileNotChangedException extends BusinessException {
    public ProfileNotChangedException() {
        super(ErrorCode.PROFILE_NOT_CHANGED);
    }
}
