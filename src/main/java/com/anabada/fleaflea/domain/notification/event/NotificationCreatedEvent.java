package com.anabada.fleaflea.domain.notification.event;

import com.anabada.fleaflea.domain.notification.dto.NotificationResponse;

public record NotificationCreatedEvent(
        Long receiverId,
        NotificationResponse notification
) {
}
