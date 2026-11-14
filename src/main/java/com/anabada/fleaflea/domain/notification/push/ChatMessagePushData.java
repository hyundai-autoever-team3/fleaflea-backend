package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.PushCategory;

import java.util.Objects;

public record ChatMessagePushData(Long roomId, Long messageId) implements PushData {

    public ChatMessagePushData {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(messageId, "messageId");
    }

    public static ChatMessagePushData of(Long roomId, Long messageId) {
        return new ChatMessagePushData(roomId, messageId);
    }

    @Override
    public PushCategory category() {
        return PushCategory.CHAT_MESSAGE;
    }
}
