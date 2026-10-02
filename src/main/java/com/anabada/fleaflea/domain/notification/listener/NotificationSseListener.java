package com.anabada.fleaflea.domain.notification.listener;

import com.anabada.fleaflea.domain.notification.event.NotificationCreatedEvent;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.global.observability.SseTaskDispatcher;
import com.anabada.fleaflea.global.observability.SseTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationSseListener {

    private final NotificationSseService notificationSseService;
    private final SseTaskDispatcher taskDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        taskDispatcher.submit(
                SseTaskType.NOTIFICATION,
                () -> notificationSseService.sendNotification(
                        event.receiverId(),
                        event.notification()
                )
        );
    }
}
