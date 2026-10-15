package com.anabada.fleaflea.domain.notification;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.member.service.MemberAuthService;
import com.anabada.fleaflea.domain.notification.domain.PushPlatform;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceNotOwnerException;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceLimitExceededException;
import com.anabada.fleaflea.domain.notification.ratelimit.PushDeviceRegistrationRateLimiter;
import com.anabada.fleaflea.domain.notification.push.PushTarget;
import com.anabada.fleaflea.domain.notification.repository.PushDeviceRepository;
import com.anabada.fleaflea.domain.notification.service.PushDeviceService;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@PostgresIntegrationTest
@Import(RedisTestContainerConfiguration.class)
@Sql(statements = "TRUNCATE TABLE members RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "TRUNCATE TABLE members RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PushDeviceIntegrationTest {

    private static final String FID = "c1234567890123456789012";

    @Autowired
    private PushDeviceService service;

    @Autowired
    private PushDeviceRepository devices;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MemberAuthService auth;

    // 등록 수·DB 경합만 검증하며 시간 제한은 별도 단위 테스트에서 검증한다.
    @MockitoBean
    private PushDeviceRegistrationRateLimiter registrationRateLimiter;

    private Member first;

    private Member second;

    @BeforeEach
    void setUp() {
        first = members.save(MemberFixture.createMember("first"));
        second = members.save(MemberFixture.createMember("second"));
    }

    @Test
    @DisplayName("기기 열 개를 보유하면 새 등록은 거절하지만 기존 갱신과 삭제 후 등록은 허용한다")
    void register_enforcesLimitAndAllowsRefreshAndReplacement() {
        for (int index = 0; index < 10; index++) {
            service.registerPushDevice(first.getMemberId(), fid(index), PushPlatform.WEB);
        }
        Long originalId = service.findPushTargets(first.getMemberId()).stream()
                .filter(target -> target.fid().equals(fid(0)))
                .findFirst()
                .orElseThrow()
                .pushDeviceId();

        assertThatThrownBy(() -> service.registerPushDevice(first.getMemberId(), fid(10), PushPlatform.WEB))
                .isInstanceOf(PushDeviceLimitExceededException.class);
        assertThat(service.registerPushDevice(first.getMemberId(), fid(0), PushPlatform.WEB))
                .isEqualTo(originalId);
        assertThat(service.findPushTargets(first.getMemberId())).hasSize(10);

        service.deletePushDevice(first.getMemberId(), originalId);
        service.registerPushDevice(first.getMemberId(), fid(10), PushPlatform.WEB);

        assertThat(service.findPushTargets(first.getMemberId())).hasSize(10);
    }

    @Test
    @DisplayName("기기가 가득 찬 회원으로 계정을 전환하면 기존 소유자의 등록을 보존한다")
    void register_accountSwitchRespectsNewOwnerLimit() {
        for (int index = 0; index < 10; index++) {
            service.registerPushDevice(second.getMemberId(), fid(index), PushPlatform.WEB);
        }
        Long originalId = service.registerPushDevice(first.getMemberId(), fid(10), PushPlatform.WEB);

        assertThatThrownBy(() -> service.registerPushDevice(second.getMemberId(), fid(10), PushPlatform.WEB))
                .isInstanceOf(PushDeviceLimitExceededException.class);
        assertThat(service.findPushTargets(first.getMemberId())).singleElement()
                .satisfies(target -> assertThat(target.pushDeviceId()).isEqualTo(originalId));
        assertThat(service.findPushTargets(second.getMemberId())).hasSize(10);
    }

    @Test
    @DisplayName("아홉 기기를 보유한 회원이 동시에 등록해도 한 요청만 성공한다")
    void register_concurrently_doesNotExceedMemberLimit() throws Exception {
        for (int index = 0; index < 9; index++) {
            service.registerPushDevice(first.getMemberId(), fid(index), PushPlatform.WEB);
        }
        int requestCount = 8;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                String newFid = fid(10 + index);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS))
                        throw new IllegalStateException("barrier timeout");
                    try {
                        service.registerPushDevice(first.getMemberId(), newFid, PushPlatform.WEB);
                        return true;
                    } catch (PushDeviceLimitExceededException exception) {
                        return false;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS))
                    successCount++;
            }
            assertThat(successCount).isEqualTo(1);
        }
        assertThat(service.findPushTargets(first.getMemberId())).hasSize(10);
    }

    private static String fid(int index) {
        return "c" + String.format(Locale.ROOT, "%021d", index);
    }

    @Test
    @DisplayName("재등록과 계정 전환은 하나의 기기를 갱신하며 이전 실패로 새 등록을 삭제하지 않는다")
    void register_switchesAccountAndProtectsNewRegistration() {
        Long deviceId = service.registerPushDevice(first.getMemberId(), FID, PushPlatform.WEB);
        PushTarget old = service.findPushTargets(first.getMemberId()).getFirst();

        assertThat(service.registerPushDevice(first.getMemberId(), FID, PushPlatform.WEB))
                .isEqualTo(deviceId);
        assertThat(service.deleteUnregisteredPushDevice(old)).isZero();

        PushTarget refreshed = service.findPushTargets(first.getMemberId()).getFirst();

        service.registerPushDevice(second.getMemberId(), FID, PushPlatform.WEB);

        assertThat(devices.count()).isEqualTo(1);
        assertThat(service.findPushTargets(first.getMemberId())).isEmpty();
        assertThat(service.findPushTargets(second.getMemberId())).hasSize(1);
        assertThat(service.deleteUnregisteredPushDevice(refreshed)).isZero();
        assertThat(service.deleteUnregisteredPushDevice(service.findPushTargets(second.getMemberId()).getFirst()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("타인 기기를 삭제할 수 없으며 본인 삭제와 없는 등록 삭제는 반복 가능하다")
    void delete_checksOwnerAndIsIdempotent() {
        Long deviceId = service.registerPushDevice(first.getMemberId(), FID, PushPlatform.WEB);

        assertThatThrownBy(() -> service.deletePushDevice(second.getMemberId(), deviceId))
                .isInstanceOf(PushDeviceNotOwnerException.class);
        assertThat(devices.count()).isEqualTo(1);

        service.deletePushDevice(first.getMemberId(), deviceId);
        service.deletePushDevice(first.getMemberId(), deviceId);

        assertThat(devices.count()).isZero();
    }

    @Test
    @DisplayName("로그아웃은 모든 기기를 해제하고 회원 삭제는 FK로 등록을 정리한다")
    void logoutAndDeletion_removeRegistrations() {
        service.registerPushDevice(first.getMemberId(), FID, PushPlatform.WEB);
        service.registerPushDevice(first.getMemberId(), "d1234567890123456789012", PushPlatform.WEB);
        service.registerPushDevice(second.getMemberId(), "e1234567890123456789012", PushPlatform.WEB);
        auth.logout(first.getMemberId());

        assertThat(service.findPushTargets(first.getMemberId())).isEmpty();
        assertThat(service.findPushTargets(second.getMemberId())).hasSize(1);

        members.deleteById(second.getMemberId());

        assertThat(devices.count()).isZero();
    }

    @Test
    @DisplayName("서로 다른 회원이 같은 FID를 동시에 등록해도 등록은 하나만 남는다")
    void register_concurrently_preservesUniqueInstallation() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> a = executor.submit(() -> registerAfterBarrier(first.getMemberId(), ready, start));
            Future<?> b = executor.submit(() -> registerAfterBarrier(second.getMemberId(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
        assertThat(devices.count()).isEqualTo(1);
        assertThat(service.findPushTargets(first.getMemberId()).size() + service.findPushTargets(second.getMemberId()).size())
                .isEqualTo(1);
    }

    private void registerAfterBarrier(Long memberId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("barrier timeout");
            service.registerPushDevice(memberId, FID, PushPlatform.WEB);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
