package com.anabada.fleaflea.domain.notification.push;

import java.util.UUID;
import java.util.Objects;

// 설치 식별자가 자동 생성된 toString을 통해 로그에 노출되지 않도록 한다.
public record PushTarget(Long pushDeviceId, Long memberId, String fid, UUID registrationVersion) {

    public PushTarget {
        Objects.requireNonNull(pushDeviceId, "pushDeviceId");
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(fid, "fid");
        Objects.requireNonNull(registrationVersion, "registrationVersion");
    }

    public static PushTarget of(
            Long pushDeviceId,
            Long memberId,
            String fid,
            UUID registrationVersion
    ) {
        return new PushTarget(pushDeviceId, memberId, fid, registrationVersion);
    }

    @Override
    public String toString() {
        return "PushTarget[pushDeviceId=" + pushDeviceId + "]";
    }
}
