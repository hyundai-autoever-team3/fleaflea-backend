package com.anabada.fleaflea.domain.begrequest.exception;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;

public class BegRequestNotApplicantException extends BusinessException {
    public BegRequestNotApplicantException() {
        super(ErrorCode.BEG_REQUEST_NOT_APPLICANT);
    }
}
