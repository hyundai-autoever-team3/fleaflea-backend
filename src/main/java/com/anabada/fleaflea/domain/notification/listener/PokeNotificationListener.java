package com.anabada.fleaflea.domain.notification.listener;

import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.service.NotificationMessageFactory;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import com.anabada.fleaflea.domain.poke.event.MemberPokedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PokeNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            MemberPokedEvent event
    ) {
        notificationService.createNotification(
                event.recipientId(),
                NotificationType.POKE_RECEIVED,
                NotificationReferenceType.MEMBER_POKE,
                event.pokeId(),
                NotificationMessageFactory.pokeReceived(
                        event.senderNickname()
                )
        );
    }
}