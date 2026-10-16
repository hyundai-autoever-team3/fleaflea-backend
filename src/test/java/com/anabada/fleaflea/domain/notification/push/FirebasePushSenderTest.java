package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.google.firebase.messaging.MessagingErrorCode;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FirebasePushSenderTest {

    @Test
    @DisplayName("FCM 비활성화 시 Firebase 호출 없이 비활성 결과를 반환한다")
    void send_whenDisabledDoesNotCallFirebase() {
        var result = new FirebasePushSender(null, null).send(target, message);

        assertThat(result.status()).isEqualTo(PushSendStatus.DISABLED);
        assertThat(result.errorCode()).isNull();
        verifyNoInteractions(messaging);
    }

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final FirebasePushSender sender = new FirebasePushSender(messaging, URI.create("https://app.example.test/"));
    private final PushTarget target = PushTarget.of(1L, 2L, "c123456789012345678901", UUID.randomUUID());
    private final PushMessage message = PushMessage.of("title", "preview", ChatMessagePushData.of(1L, 2L));

    @Test
    @DisplayName("전송은 FID를 대상으로 제목과 본문 및 이동 정보를 전달한다")
    void send_targetsRegisteredInstallation() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("provider-message-id");

        PushSendResult result = sender.send(target, message);

        assertThat(result.status()).isEqualTo(PushSendStatus.ACCEPTED);
        assertThat(result.errorCode()).isNull();

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);

        verify(messaging).send(sent.capture());
        assertThat(ReflectionTestUtils.getField(sent.getValue(), "fid")).isEqualTo(target.fid());
        assertThat(ReflectionTestUtils.getField(sent.getValue(), "data"))
                .isEqualTo(Map.of("category", "CHAT_MESSAGE", "roomId", "1", "messageId", "2"));

        Object notification = ReflectionTestUtils.getField(sent.getValue(), "notification");

        assertThat(ReflectionTestUtils.getField(notification, "body")).isEqualTo("preview");

        Object webpush = ReflectionTestUtils.getField(sent.getValue(), "webpushConfig");
        Object options = ReflectionTestUtils.getField(webpush, "fcmOptions");
        String link = (String) ReflectionTestUtils.getField(options, "link");

        assertThat(link).startsWith("https://app.example.test/?")
                .contains("push_category=CHAT_MESSAGE", "push_roomId=1", "push_messageId=2");
    }

    @Test
    @DisplayName("localhost 검증 모드는 클릭 링크 없이 기존 알림과 식별자를 전송한다")
    void send_withoutClickLinkPreservesNotificationPayload() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("provider-message-id");
        FirebasePushSender localSender = new FirebasePushSender(messaging, null);

        assertThat(localSender.send(target, message).status()).isEqualTo(PushSendStatus.ACCEPTED);

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);

        verify(messaging).send(sent.capture());
        assertThat(ReflectionTestUtils.getField(sent.getValue(), "webpushConfig")).isNull();
        assertThat(ReflectionTestUtils.getField(sent.getValue(), "notification")).isNotNull();
        assertThat(ReflectionTestUtils.getField(sent.getValue(), "data"))
                .isEqualTo(Map.of("category", "CHAT_MESSAGE", "roomId", "1", "messageId", "2"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(NotificationType.class)
    @DisplayName("모든 일반 알림 유형을 기존 문자열 필드와 클릭 링크로 변환해 전송한다")
    void send_notificationPreservesWireContract(NotificationType type) throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("provider-message-id");
        PushMessage notification = PushMessage.of("Fleaflea", "알림 내용",
                NotificationPushData.of(10L, type, NotificationReferenceType.FRIENDSHIP, 7L));

        sender.send(target, notification);

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);

        verify(messaging).send(sent.capture());
        assertThat(ReflectionTestUtils.getField(sent.getValue(), "data")).isEqualTo(Map.of(
                "category", "NOTIFICATION", "notificationId", "10", "type", type.name(),
                "referenceType", "FRIENDSHIP", "referenceId", "7"));

        Object webpush = ReflectionTestUtils.getField(sent.getValue(), "webpushConfig");
        Object options = ReflectionTestUtils.getField(webpush, "fcmOptions");
        String link = (String) ReflectionTestUtils.getField(options, "link");

        assertThat(link).contains("push_category=NOTIFICATION", "push_notificationId=10",
                "push_type=" + type.name(), "push_referenceType=FRIENDSHIP", "push_referenceId=7");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MessagingErrorCode.class)
    @DisplayName("미등록 오류만 기기 정리 대상으로 분류하고 나머지 오류는 일반 실패로 남긴다")
    void send_onlyUnregisteredRemovesDevice(MessagingErrorCode code) throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        when(messaging.send(any(Message.class))).thenThrow(exception);
        var result = sender.send(target, message);

        assertThat(result.status()).isEqualTo(code == MessagingErrorCode.UNREGISTERED
                ? PushSendStatus.UNREGISTERED : PushSendStatus.FAILED);
        assertThat(result.errorCode()).isSameAs(code);
    }

    @Test
    @DisplayName("FCM 예외에 구체적인 오류 코드가 없으면 null과 실패 상태로 반환한다")
    void send_missingErrorCodePreservesNull() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        PushSendResult result = sender.send(target, message);

        assertThat(result.status()).isEqualTo(PushSendStatus.FAILED);
        assertThat(result.errorCode()).isNull();
    }
}
