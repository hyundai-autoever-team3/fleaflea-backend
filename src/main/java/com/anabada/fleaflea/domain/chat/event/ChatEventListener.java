package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
public class ChatEventListener {
    private final NotificationSseService sseService;

    @Async("notificationSseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatEvent(ChatEvent event) {
        sseService.sendEvent(event.firstMemberId(), event.name(), event.payload());
        sseService.sendEvent(event.secondMemberId(), event.name(), event.payload());
    }
}
