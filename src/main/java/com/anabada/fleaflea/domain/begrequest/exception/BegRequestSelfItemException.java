package com.anabada.fleaflea.domain.begrequest.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class BegRequestSelfItemException extends BusinessException {
    public BegRequestSelfItemException() {
        super(ErrorCode.BEG_REQUEST_SELF_ITEM);
    }
}
