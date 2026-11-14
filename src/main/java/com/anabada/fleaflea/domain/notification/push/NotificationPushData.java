package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.domain.PushCategory;

import java.util.Objects;

public record NotificationPushData(
        Long notificationId,
        NotificationType type,
        NotificationReferenceType referenceType,
        Long referenceId
) implements PushData {

    public NotificationPushData {
        Objects.requireNonNull(notificationId, "notificationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(referenceType, "referenceType");
        Objects.requireNonNull(referenceId, "referenceId");
    }

    public static NotificationPushData of(
            Long notificationId,
            NotificationType type,
            NotificationReferenceType referenceType,
            Long referenceId
    ) {
        return new NotificationPushData(notificationId, type, referenceType, referenceId);
    }

    @Override
    public PushCategory category() {
        return PushCategory.NOTIFICATION;
    }
}
