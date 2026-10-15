package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.anabada.fleaflea.global.config.NotificationPushConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FCM_INTEGRATION_TEST", matches = "true")
class FcmConnectionTest {

    @Test
    @DisplayName("개발 프로젝트의 지정한 웹 설치에 테스트 푸시를 실제 전송한다")
    void send_acceptsPushForDevelopmentInstallation() throws Exception {
        String projectId = required("FCM_PROJECT_ID");
        URI webUrl = URI.create(required("FCM_WEB_APP_URL"));
        String fid = required("FCM_TEST_FID");

        assertThat(webUrl.getScheme()).isEqualTo("https");
        assertThat(fid).matches("[A-Za-z0-9_-]{22}");

        FirebaseApp app = new NotificationPushConfig().pushFirebaseApp(projectId);
        try {
            FirebasePushSender sender = new FirebasePushSender(FirebaseMessaging.getInstance(app), webUrl);
            PushSendResult result = sender.send(PushTarget.of(0L, 0L, fid, UUID.randomUUID()),
                    PushMessage.of("Fleaflea 개발 테스트", "웹 푸시 연결 확인", ChatMessagePushData.of(0L, 0L)));
            assertThat(result.status()).as("FCM 접수 상태 (%s)", result.errorCode())
                    .isEqualTo(PushSendStatus.ACCEPTED);
        } finally {
            app.delete();
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank())
            throw new IllegalStateException(name + " is required");
        return value;
    }
}
