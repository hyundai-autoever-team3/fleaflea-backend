package com.anabada.fleaflea.domain.notification.listener;

import com.anabada.fleaflea.domain.friendship.event.FriendAcceptedEvent;
import com.anabada.fleaflea.domain.friendship.event.FriendRequestedEvent;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.service.NotificationMessageFactory;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FriendNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            FriendRequestedEvent event
    ) {
        notificationService.createNotification(
                event.addresseeId(),
                NotificationType.FRIEND_REQUESTED,
                NotificationReferenceType.FRIENDSHIP,
                event.friendshipId(),
                NotificationMessageFactory.friendRequested(
                        event.requesterNickname()
                )
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            FriendAcceptedEvent event
    ) {
        notificationService.createNotification(
                event.requesterId(),
                NotificationType.FRIEND_ACCEPTED,
                NotificationReferenceType.FRIENDSHIP,
                event.friendshipId(),
                NotificationMessageFactory.friendAccepted(
                        event.addresseeNickname()
                )
        );
    }
}