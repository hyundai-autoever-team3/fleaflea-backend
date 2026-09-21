package com.anabada.fleaflea.domain.notification.notifier;

import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.service.NotificationMessageFactory;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import com.anabada.fleaflea.domain.poke.event.MemberPokedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PokeNotifier {

    private final NotificationService notificationService;

    public void notifyOf(
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