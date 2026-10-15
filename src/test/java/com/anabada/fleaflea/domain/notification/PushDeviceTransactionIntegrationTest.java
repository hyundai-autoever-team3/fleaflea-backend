package com.anabada.fleaflea.domain.notification;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.domain.PushPlatform;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationRateLimitExceededException;
import com.anabada.fleaflea.domain.notification.ratelimit.PushDeviceRegistrationRateLimiter;
import com.anabada.fleaflea.domain.notification.service.PushDeviceService;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

@PostgresIntegrationTest
class PushDeviceTransactionIntegrationTest {

    @Autowired
    private PushDeviceService service;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MemberRepository members;

    @MockitoBean
    private PushDeviceRegistrationRateLimiter limiter;

    @MockitoBean
    private ImageService imageService;

    private Member member;

    @AfterEach
    void tearDown() {
        if (member != null)
            members.deleteById(member.getMemberId());
    }

    @Test
    @DisplayName("한도 초과 요청은 실제 트랜잭션과 DB 연결 없이 거절한다")
    void register_rejectsBeforeOpeningDatabaseConnection() {
        doAnswer(invocation -> {
            assertNoTransactionOrConnection();
            throw new PushDeviceRegistrationRateLimitExceededException();
        }).when(limiter).acquire(1L);

        assertThatThrownBy(() -> service.registerPushDevice(1L, "c1234567890123456789012", PushPlatform.WEB))
                .isInstanceOf(PushDeviceRegistrationRateLimitExceededException.class);
    }

    @Test
    @DisplayName("한도 검사 후 별도 트랜잭션에서 회원 잠금과 기기 저장을 수행한다")
    void register_opensWriteTransactionAfterRateLimitCheck() {
        member = members.save(MemberFixture.createMember("push-transaction"));
        doAnswer(invocation -> {
            assertNoTransactionOrConnection();
            return null;
        }).when(limiter).acquire(member.getMemberId());

        Long deviceId = service.registerPushDevice(member.getMemberId(), "c1234567890123456789012", PushPlatform.WEB);

        assertThat(service.findPushTargets(member.getMemberId())).singleElement()
                .satisfies(target -> assertThat(target.pushDeviceId()).isEqualTo(deviceId));
    }

    private void assertNoTransactionOrConnection() throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections())
                .isZero();
    }
}
