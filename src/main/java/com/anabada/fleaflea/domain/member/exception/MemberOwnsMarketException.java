package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MemberOwnsMarketException extends BusinessException {

    public MemberOwnsMarketException() {
        super(ErrorCode.MEMBER_OWNS_MARKET);
    }
}