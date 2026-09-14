package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MemberNicknameDuplicateException extends BusinessException {
    public MemberNicknameDuplicateException() {
        super(ErrorCode.DUPLICATE_NICKNAME);
    }
}
