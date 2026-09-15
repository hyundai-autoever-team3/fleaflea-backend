package com.anabada.fleaflea.domain.market.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MarketNotParticipantException extends BusinessException {

    public MarketNotParticipantException() {
        super(ErrorCode.MARKET_NOT_PARTICIPANT);
    }
}
