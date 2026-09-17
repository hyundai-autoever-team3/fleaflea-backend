package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class InvalidRentalPeriodException extends BusinessException {

    public InvalidRentalPeriodException() {
        super(ErrorCode.INVALID_RENTAL_PERIOD);
    }
}