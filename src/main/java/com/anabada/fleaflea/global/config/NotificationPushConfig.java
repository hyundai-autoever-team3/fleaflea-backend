package com.anabada.fleaflea.global.config;

import com.anabada.fleaflea.domain.notification.push.FirebasePushSender;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;

@Configuration(proxyBeanMethods = false)
public class NotificationPushConfig {

    @Bean(destroyMethod = "delete")
    @ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
    public FirebaseApp pushFirebaseApp(@Value("${fcm.project-id}") String projectId) throws IOException {
        if (projectId.isBlank()) {
            throw new IllegalStateException("FCM_PROJECT_ID is required when FCM is enabled");
        }
        FirebaseOptions firebaseOptions = FirebaseOptions.builder()
                .setProjectId(projectId)
                // On GCE, ADC uses the attached runtime service account. No key file is needed.
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setConnectTimeout(3000)
                .setReadTimeout(5000)
                .setWriteTimeout(5000)
                .build();

        return FirebaseApp.initializeApp(firebaseOptions, "fleaflea-push");
    }

    @Bean
    @ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
    public FirebasePushSender firebasePushSender(
            FirebaseApp pushFirebaseApp,
            @Value("${fcm.web-app-url}") String webAppUrl,
            @Value("${fcm.web-click-link-enabled:true}") boolean webClickLinkEnabled
    ) {
        URI webAppUri = null;
        if (webClickLinkEnabled) {
            webAppUri = URI.create(webAppUrl);
            validateWebAppUrl(webAppUri);
        }

        return new FirebasePushSender(FirebaseMessaging.getInstance(pushFirebaseApp), webAppUri);
    }

    @Bean
    @ConditionalOnProperty(name = "fcm.enabled", havingValue = "false", matchIfMissing = true)
    public FirebasePushSender disabledPushSender() {
        return new FirebasePushSender(null, null);
    }

    private void validateWebAppUrl(URI webAppUri) {
        if (!"https".equals(webAppUri.getScheme())
                || webAppUri.getHost() == null
                || webAppUri.getUserInfo() != null
                || webAppUri.getQuery() != null
                || webAppUri.getFragment() != null) {
            throw new IllegalStateException("FCM_WEB_APP_URL must be an HTTPS frontend URL without credentials, query or fragment");
        }
    }
}
