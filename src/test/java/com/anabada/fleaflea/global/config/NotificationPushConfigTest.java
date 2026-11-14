package com.anabada.fleaflea.global.config;

import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.anabada.fleaflea.domain.notification.push.FirebasePushSender;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class NotificationPushConfigTest {

    @Test
    @DisplayName("기본 비활성 설정에서는 ADC 조회 없이 기동한다")
    void disabled_doesNotLoadCredentials() {
        try (MockedStatic<GoogleCredentials> credentials = mockStatic(GoogleCredentials.class)) {
            new ApplicationContextRunner().withUserConfiguration(NotificationPushConfig.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed().doesNotHaveBean(FirebaseApp.class);
                        assertThat(context).hasSingleBean(FirebasePushSender.class);
                        assertThat(context.getBean(FirebasePushSender.class).send(null, null).status())
                                .isEqualTo(PushSendStatus.DISABLED);
                    });
            credentials.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("ADC 자격 증명과 명시한 대상 Firebase 프로젝트로 초기화한다")
    void enabled_usesAdcAndExplicitTargetProject() throws Exception {
        GoogleCredentials adc = GoogleCredentials.create(new AccessToken("test-only-token", null));
        try (MockedStatic<GoogleCredentials> credentials = mockStatic(GoogleCredentials.class)) {
            credentials.when(GoogleCredentials::getApplicationDefault).thenReturn(adc);
            FirebaseApp app = new NotificationPushConfig().pushFirebaseApp("example-dev-firebase");
            try {
                assertThat(app.getOptions().getProjectId()).isEqualTo("example-dev-firebase");
                credentials.verify(GoogleCredentials::getApplicationDefault);
            } finally {
                app.delete();
            }
        }
    }

    @ParameterizedTest(name = "webAppUrl={0}")
    @ValueSource(strings = {"", "http://localhost:8765/", "https://app.example.test/?token=test"})
    @DisplayName("클릭 링크를 사용하면 localhost 검증 설정과 별개로 기존 HTTPS 주소 검증을 유지한다")
    void enabledClickLink_requiresValidHttpsDestination(String webAppUrl) {
        assertThatThrownBy(() -> new NotificationPushConfig().firebasePushSender(null, webAppUrl, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FCM_WEB_APP_URL");
    }

    @Test
    @DisplayName("푸시가 활성화됐는데 프로젝트 ID가 없으면 초기화를 거절한다")
    void enabled_requiresProjectId() {
        assertThatThrownBy(() -> new NotificationPushConfig().pushFirebaseApp(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FCM_PROJECT_ID");
    }

}
