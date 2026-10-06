package com.anabada.fleaflea.domain.poke.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class PokeNotFoundException extends BusinessException {

    public PokeNotFoundException() {
        super(ErrorCode.POKE_NOT_FOUND);
    }
}
