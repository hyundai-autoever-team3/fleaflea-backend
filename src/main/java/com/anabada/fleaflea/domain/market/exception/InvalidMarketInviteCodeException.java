package com.anabada.fleaflea.domain.market.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidMarketInviteCodeException extends BusinessException {

    public InvalidMarketInviteCodeException() {
        super(ErrorCode.INVALID_MARKET_INVITE_CODE);
    }
}