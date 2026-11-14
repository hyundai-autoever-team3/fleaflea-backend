package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RequiredArgsConstructor
public class FirebasePushSender {

    private final FirebaseMessaging firebaseMessaging;
    private final URI webAppUrl;

    public PushSendResult send(
            PushTarget pushTarget,
            PushMessage pushMessage
    ) {
        if (firebaseMessaging == null) {
            return PushSendResult.of(PushSendStatus.DISABLED, null);
        }
        try {
            Message message = createMessage(pushTarget, pushMessage);

            firebaseMessaging.send(message);

            return PushSendResult.of(PushSendStatus.ACCEPTED, null);
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode messagingErrorCode = exception.getMessagingErrorCode();
            PushSendStatus status = messagingErrorCode == MessagingErrorCode.UNREGISTERED
                    ? PushSendStatus.UNREGISTERED
                    : PushSendStatus.FAILED;
            return PushSendResult.of(status, messagingErrorCode);
        }
    }

    private Message createMessage(
            PushTarget pushTarget,
            PushMessage pushMessage
    ) {
        Notification notification = Notification.builder()
                .setTitle(pushMessage.title())
                .setBody(pushMessage.body())
                .build();

        Map<String, String> data = createData(pushMessage.data());
        Message.Builder messageBuilder = Message.builder()
                .setFid(pushTarget.fid())
                .setNotification(notification)
                .putAllData(data);

        if (webAppUrl != null) {
            messageBuilder.setWebpushConfig(WebpushConfig.builder()
                    .setFcmOptions(WebpushFcmOptions.builder()
                            .setLink(resolveClickLink(data))
                            .build())
                    .build());
        }

        return messageBuilder.build();
    }

    private Map<String, String> createData(PushData data) {
        return switch (data) {
            case NotificationPushData notification -> Map.of(
                    "category", notification.category().name(),
                    "notificationId", notification.notificationId().toString(),
                    "type", notification.type().name(),
                    "referenceType", notification.referenceType().name(),
                    "referenceId", notification.referenceId().toString()
            );
            case ChatMessagePushData chat -> Map.of(
                    "category", chat.category().name(),
                    "roomId", chat.roomId().toString(),
                    "messageId", chat.messageId().toString()
            );
        };
    }

    private String resolveClickLink(Map<String, String> data) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(webAppUrl);

        data.forEach((key, value) -> uriBuilder.queryParam("push_" + key, value));

        return uriBuilder.build().encode().toUriString();
    }
}
