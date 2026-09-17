package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class TradeRequestNotParticipantException extends BusinessException {

    public TradeRequestNotParticipantException() {
        super(ErrorCode.TRADE_REQUEST_NOT_PARTICIPANT);
    }
}