package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.google.firebase.messaging.MessagingErrorCode;

import java.util.Objects;

// 성공·비활성화 또는 SDK가 구체적인 실패 코드를 제공하지 않은 경우 errorCode는 null이다.
public record PushSendResult(PushSendStatus status, MessagingErrorCode errorCode) {

    public PushSendResult {
        Objects.requireNonNull(status, "status");
    }

    public static PushSendResult of(
            PushSendStatus status,
            MessagingErrorCode errorCode
    ) {
        return new PushSendResult(status, errorCode);
    }
}
