package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.global.config.NotificationPushConfig;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FCM_LOCAL_INTEGRATION_TEST", matches = "true")
class FcmLocalConnectionTest {

    @Test
    @DisplayName("localhost 개발 브라우저에 채팅과 일반 알림의 제목 및 본문 확인용 푸시를 전송한다")
    void send_acceptsPushForLocalBrowser() throws Exception {
        String projectId = required("FCM_PROJECT_ID");
        String fid = required("FCM_TEST_FID");

        assertThat(fid).matches("[A-Za-z0-9_-]{22}");

        FirebaseApp firebaseApp = new NotificationPushConfig().pushFirebaseApp(projectId);
        try {
            // 표시 진단만 수행하므로 DB와 연결되지 않은 합성 식별자를 사용하고 클릭 링크는 생략한다.
            FirebasePushSender sender = new FirebasePushSender(FirebaseMessaging.getInstance(firebaseApp), null);
            PushTarget target = PushTarget.of(1L, 1L, fid, UUID.randomUUID());
            List<PushMessage> messages = List.of(
                    PushMessage.of("민지", "채팅 푸시 테스트: 내일 몇 시에 만날까요? 😀",
                            ChatMessagePushData.of(1L, 1L)),
                    PushMessage.of("Fleaflea", "테스트 회원님이 콕 찔렀어요.",
                            NotificationPushData.of(1L, NotificationType.POKE_RECEIVED,
                                    NotificationReferenceType.MEMBER_POKE, 1L))
            );

            for (PushMessage message : messages) {
                assertThat(sender.send(target, message).status())
                        .as("%s 표시 진단용 푸시의 FCM 접수", message.category())
                        .isEqualTo(PushSendStatus.ACCEPTED);
            }
        } finally {
            firebaseApp.delete();
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }

        return value;
    }
}
