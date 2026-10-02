package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.global.observability.SseTaskDispatcher;
import com.anabada.fleaflea.global.observability.SseTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
public class ChatEventListener {
    private final NotificationSseService sseService;
    private final SseTaskDispatcher taskDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatEvent(ChatEvent event) {
        taskDispatcher.submit(SseTaskType.CHAT, () -> {
            sseService.sendEvent(event.firstMemberId(), event.name(), event.payload());
            sseService.sendEvent(event.secondMemberId(), event.name(), event.payload());
        });
    }
}
