package com.anabada.fleaflea.domain.poke.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class PokeLimitExceededException extends BusinessException {

    public PokeLimitExceededException() {
        super(ErrorCode.POKE_LIMIT_EXCEEDED);
    }
}
