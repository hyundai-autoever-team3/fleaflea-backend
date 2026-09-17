package com.anabada.fleaflea.domain.trade.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class RentalPeriodRequiredException extends BusinessException {

    public RentalPeriodRequiredException() {
        super(ErrorCode.RENTAL_PERIOD_REQUIRED);
    }
}