package com.anabada.fleaflea.domain.member.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MemberWithdrawalBlockedException extends BusinessException {

    public MemberWithdrawalBlockedException() {
        super(ErrorCode.MEMBER_WITHDRAWAL_BLOCKED);
    }
}