package com.anabada.fleaflea.domain.notification;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.member.service.MemberAuthService;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.domain.notification.sse.SseEmitterRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@Import(RedisTestContainerConfiguration.class)
class NotificationSseLifecycleIntegrationTest {

    @Autowired
    private MemberRepository members;

    @Autowired
    private MemberAuthService auth;

    @Autowired
    private NotificationSseService sse;

    @Autowired
    private SseEmitterRepository emitters;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ImageService imageService;

    private Member member;

    private Member other;

    @BeforeEach
    void setUp() {
        member = members.save(MemberFixture.createMember("sse-logout"));
        other = members.save(MemberFixture.createMember("sse-other"));
        sse.subscribe(member.getMemberId());
        sse.subscribe(member.getMemberId());
        sse.subscribe(other.getMemberId());
    }

    @AfterEach
    void tearDown() {
        sse.disconnectMember(member.getMemberId());
        sse.disconnectMember(other.getMemberId());
        members.deleteById(member.getMemberId());
        members.deleteById(other.getMemberId());
    }

    @Test
    @DisplayName("로그아웃 커밋 후 본인의 모든 SSE 연결만 종료한다")
    void logout_closesConnectionsOnlyAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            auth.logout(member.getMemberId(), null);
            assertThat(emitters.findAllByMemberId(member.getMemberId())).hasSize(2);
        });

        assertThat(emitters.findAllByMemberId(member.getMemberId())).isEmpty();
        assertThat(emitters.findAllByMemberId(other.getMemberId())).hasSize(1);
    }

    @Test
    @DisplayName("로그아웃 트랜잭션이 롤백되면 SSE 연결을 유지한다")
    void logout_preservesConnectionsAfterRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            auth.logout(member.getMemberId(), null);
            status.setRollbackOnly();
        });

        assertThat(emitters.findAllByMemberId(member.getMemberId())).hasSize(2);
        assertThat(emitters.findAllByMemberId(other.getMemberId())).hasSize(1);
    }
}
