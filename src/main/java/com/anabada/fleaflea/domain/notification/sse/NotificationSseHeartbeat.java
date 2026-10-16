
package com.anabada.fleaflea.domain.notification.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationSseHeartbeat {

    private static final long INTERVAL_MILLIS = 30_000L;

    private final NotificationSseService notificationSseService;

    @Async("notificationSseExecutor")
    @Scheduled(fixedDelay = INTERVAL_MILLIS)
    public void heartbeat() {
        notificationSseService.sendHeartbeat();
    }
}
