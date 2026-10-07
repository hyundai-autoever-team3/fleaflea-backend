package com.anabada.fleaflea.domain.poke.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class PokeAccessDeniedException extends BusinessException {

    public PokeAccessDeniedException() {
        super(ErrorCode.POKE_ACCESS_DENIED);
    }
}
