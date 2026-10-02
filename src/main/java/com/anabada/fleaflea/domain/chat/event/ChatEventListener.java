package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final NotificationSseService notificationSseService;

    @Async("notificationSseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatEvent(ChatEvent event) {
        notificationSseService.sendEvent(event.firstMemberId(), event.eventName(), event.payload());
        notificationSseService.sendEvent(event.secondMemberId(), event.eventName(), event.payload());
    }
}
