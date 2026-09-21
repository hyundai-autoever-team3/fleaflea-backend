package com.anabada.fleaflea.domain.notification.notifier;

import com.anabada.fleaflea.domain.friendship.event.FriendAcceptedEvent;
import com.anabada.fleaflea.domain.friendship.event.FriendRequestedEvent;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.service.NotificationMessageFactory;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendNotifier {

    private final NotificationService notificationService;

    public void notifyOf(
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

    public void notifyOf(
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