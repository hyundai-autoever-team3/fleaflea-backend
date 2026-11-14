package com.anabada.fleaflea.domain.notification.listener;

import com.anabada.fleaflea.domain.chat.event.ChatMessageSentEvent;
import com.anabada.fleaflea.domain.notification.event.NotificationCreatedEvent;
import com.anabada.fleaflea.domain.notification.service.PushDeliveryService;
import com.anabada.fleaflea.domain.notification.push.PushMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
public class NotificationPushListener {

    private final PushDeliveryService pushDeliveryService;

    @Async("notificationPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        pushDeliveryService.sendPushMessage(
                event.receiverId(),
                PushMessage.from(event.notification())
        );
    }

    @Async("notificationPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSent(ChatMessageSentEvent event) {
        pushDeliveryService.sendPushMessage(
                event.friendId(),
                PushMessage.of(event.senderNickname(), event.chatMessageResponse())
        );
    }
}
