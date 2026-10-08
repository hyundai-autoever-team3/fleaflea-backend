package com.anabada.fleaflea.domain.market.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class MarketMembershipNotFoundException extends BusinessException {

    public MarketMembershipNotFoundException() {
        super(ErrorCode.MARKET_MEMBERSHIP_NOT_FOUND);
    }
}
