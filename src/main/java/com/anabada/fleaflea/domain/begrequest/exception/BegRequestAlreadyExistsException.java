package com.anabada.fleaflea.domain.begrequest.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class BegRequestAlreadyExistsException extends BusinessException {
    public BegRequestAlreadyExistsException() {
        super(ErrorCode.BEG_REQUEST_ALREADY_EXISTS);
    }
}
