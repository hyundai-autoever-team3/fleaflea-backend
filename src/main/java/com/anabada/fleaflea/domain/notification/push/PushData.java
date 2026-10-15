package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.PushCategory;

public sealed interface PushData permits NotificationPushData, ChatMessagePushData {

    PushCategory category();
}
