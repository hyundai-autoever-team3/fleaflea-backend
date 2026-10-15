package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.PushCategory;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.domain.Notification;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.domain.PushPlatform;
import com.anabada.fleaflea.domain.notification.repository.NotificationRepository;
import com.anabada.fleaflea.domain.notification.service.PushDeviceService;
import com.anabada.fleaflea.domain.poke.service.MemberPokeService;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@PostgresIntegrationTest
@Import(RedisTestContainerConfiguration.class)
@EnabledIfEnvironmentVariable(named = "FCM_NOTIFICATION_FLOW_TEST", matches = "true")
@TestPropertySource(properties = {"fcm.enabled=true", "fcm.web-click-link-enabled=false"})
class FcmNotificationFlowTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushDeviceService pushDeviceService;

    @Autowired
    private MemberPokeService memberPokeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoSpyBean
    private FirebasePushSender pushSender;

    @MockitoBean
    private ImageService imageService;

    @Test
    @DisplayName("실제 콕찌르기 알림을 저장하고 커밋 후 등록한 개발 브라우저로 FCM을 전송한다")
    void sendPoke_commitsNotificationAndSendsRealPush() throws Exception {
        String fid = System.getenv("FCM_TEST_FID");

        assertThat(fid != null && fid.matches("[A-Za-z0-9_-]{22}"))
                .as("개발 브라우저의 FCM 등록이 필요합니다").isTrue();

        Member sender = memberRepository.save(MemberFixture.createMember("push-sender"));
        Member receiver = memberRepository.save(MemberFixture.createMember("push-receiver"));
        pushDeviceService.registerPushDevice(receiver.getMemberId(), fid, PushPlatform.WEB);

        assertThat(pushDeviceService.findPushTargets(receiver.getMemberId())).hasSize(1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            memberPokeService.sendPoke(sender.getMemberId(), receiver.getMemberId());
            verifyNoInteractions(pushSender);
        });

        Notification notification = notificationRepository.findAllByReceiver_MemberId(
                receiver.getMemberId(), PageRequest.of(0, 10)).getContent().getFirst();

        assertThat(notification.getType()).isEqualTo(NotificationType.POKE_RECEIVED);
        assertThat(notification.isRead()).isFalse();

        ArgumentCaptor<PushMessage> sent = ArgumentCaptor.forClass(PushMessage.class);

        verify(pushSender, timeout(15000)).send(any(PushTarget.class), sent.capture());
        assertThat(sent.getValue().category()).isEqualTo(PushCategory.NOTIFICATION);
        assertThat(sent.getValue().data()).isInstanceOfSatisfying(NotificationPushData.class,
                data -> assertThat(data.notificationId()).isEqualTo(notification.getNotificationId()));

        awaitAcceptedPush();

        // 공개 결과에는 FID, 본문, 토큰과 자격 증명을 포함하지 않는다.
        Path resultPath = Path.of("build", "fcm-notification-flow-result.json");
        Files.createDirectories(resultPath.getParent());
        Files.writeString(resultPath, """
                {"status":"ACCEPTED","category":"NOTIFICATION","type":"POKE_RECEIVED",
                 "notificationId":"%s","committed":true,"finishedAt":"%s"}
                """.formatted(notification.getNotificationId(), Instant.now()));
    }

    private void awaitAcceptedPush() throws InterruptedException {
        var accepted = meterRegistry.counter("notification.push.send",
                "category", "NOTIFICATION", "status", "ACCEPTED");
        var failed = meterRegistry.counter("notification.push.send",
                "category", "NOTIFICATION", "status", "FAILED");
        var unregistered = meterRegistry.counter("notification.push.send",
                "category", "NOTIFICATION", "status", "UNREGISTERED");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (accepted.count() == 0 && failed.count() == 0 && unregistered.count() == 0
                && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(accepted.count()).as("일반 알림의 실제 FCM 접수 성공 횟수").isEqualTo(1);
    }
}
