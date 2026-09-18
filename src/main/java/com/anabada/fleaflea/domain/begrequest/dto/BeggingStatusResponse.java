package com.anabada.fleaflea.domain.begrequest.dto;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;

public record BeggingStatusResponse(
        Long begRequestId,
        BegRequestStatus status
) {
    public static BeggingStatusResponse from(BegRequest begRequest) {
        return new BeggingStatusResponse(
                begRequest.getBegRequestId(),
                begRequest.getStatus()
        );
    }
}
