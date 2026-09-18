package com.anabada.fleaflea.domain.begrequest.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class BegRequestAlreadyCompletedException extends BusinessException {
    public BegRequestAlreadyCompletedException() {
        super(ErrorCode.BEG_REQUEST_ALREADY_COMPLETED);
    }
}
