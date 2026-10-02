
package com.anabada.fleaflea.domain.notification.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.anabada.fleaflea.global.observability.SseTaskDispatcher;
import com.anabada.fleaflea.global.observability.SseTaskType;

@Component
@RequiredArgsConstructor
public class NotificationSseHeartbeat {

    private static final long INTERVAL_MILLIS = 30_000L;

    private final NotificationSseService notificationSseService;
    private final SseTaskDispatcher taskDispatcher;

    @Scheduled(fixedDelay = INTERVAL_MILLIS)
    public void heartbeat() {
        taskDispatcher.submit(SseTaskType.HEARTBEAT, notificationSseService::sendHeartbeat);
    }
}
