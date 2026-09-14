package com.anabada.fleaflea.domain.marketmember.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class AlreadyJoinedMarketException extends BusinessException {

    public AlreadyJoinedMarketException() {
        super(ErrorCode.ALREADY_JOINED_MARKET);
    }
}