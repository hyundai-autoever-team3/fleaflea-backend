package com.anabada.fleaflea.domain.poke.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class PokeSelfRequestException extends BusinessException {

    public PokeSelfRequestException() {
        super(ErrorCode.POKE_SELF);
    }
}
