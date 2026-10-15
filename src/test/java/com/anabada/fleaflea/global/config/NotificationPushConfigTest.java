package com.anabada.fleaflea.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class NotificationPushConfigTest {

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
