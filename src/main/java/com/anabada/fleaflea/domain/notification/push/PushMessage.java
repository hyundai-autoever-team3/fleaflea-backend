package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.notification.dto.NotificationResponse;
import com.anabada.fleaflea.domain.notification.domain.PushCategory;

import java.util.Objects;

public record PushMessage(String title, String body, PushData data) {

    public PushMessage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(data, "data");
    }

    public static PushMessage of(
            String title,
            String body,
            PushData data
    ) {
        return new PushMessage(title, body, data);
    }

    public static PushMessage from(NotificationResponse notification) {
        return of(
                "Fleaflea",
                notification.message(),
                NotificationPushData.of(
                        notification.notificationId(),
                        notification.type(),
                        notification.referenceType(),
                        notification.referenceId()
                )
        );
    }

    public static PushMessage of(
            String senderNickname,
            ChatMessageResponse message
    ) {
        String preview = createChatPreview(message.content());

        return of(
                senderNickname,
                preview,
                ChatMessagePushData.of(message.roomId(), message.id())
        );
    }

    public PushCategory category() {
        return data.category();
    }

    private static String createChatPreview(String content) {
        String preview = content.replaceAll("(?U)\\s+", " ").strip();

        if (preview.codePointCount(0, preview.length()) > 100) {
            return preview.substring(0, preview.offsetByCodePoints(0, 100)) + "…";
        }

        return preview;
    }

    @Override
    public String toString() {
        return "PushMessage[category=" + category() + "]";
    }
}
