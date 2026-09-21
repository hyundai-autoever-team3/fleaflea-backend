package com.anabada.fleaflea.domain.notification.listener;

import com.anabada.fleaflea.domain.notification.event.NotificationCreatedEvent;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationSseListener {

    private final NotificationSseService notificationSseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("notificationSseExecutor")
    public void handle(NotificationCreatedEvent event) {
        notificationSseService.sendNotification(
                event.receiverId(),
                event.notification()
        );
    }
}
