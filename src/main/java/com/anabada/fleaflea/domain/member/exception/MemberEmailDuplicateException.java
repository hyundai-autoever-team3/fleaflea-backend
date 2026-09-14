package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MemberEmailDuplicateException extends BusinessException {
    public MemberEmailDuplicateException() {
        super(ErrorCode.DUPLICATE_EMAIL);
    }
}
