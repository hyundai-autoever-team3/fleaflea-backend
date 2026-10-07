package com.anabada.fleaflea.domain.friendship.service;

import com.anabada.fleaflea.domain.friendship.exception.FriendshipAlreadyExistsException;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@Sql(statements = "TRUNCATE TABLE friendships, members RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "TRUNCATE TABLE friendships, members RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class FriendshipConcurrencyTest {

    private static final int TIMEOUT_SECONDS = 10;

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private Member firstMember;
    private Member secondMember;

    @BeforeEach
    void setUp() {
        firstMember = memberRepository.save(MemberFixture.createMember("first"));
        secondMember = memberRepository.save(MemberFixture.createMember("second"));
    }

    @ParameterizedTest(name = "두 번째 요청 역방향={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("동일하거나 반대 방향의 친구 요청이 동시에 들어와도 활성 관계는 하나만 저장한다")
    void requestFollow_concurrentRequests_createsOneRelationship(boolean reversed) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstRequest = executor.submit(() -> requestFriend(firstMember, secondMember, ready, start));
            Future<Boolean> secondRequest = executor.submit(() -> requestFriend(
                    reversed ? secondMember : firstMember, reversed ? firstMember : secondMember, ready, start));
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            start.countDown();
            boolean firstCreated = firstRequest.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            boolean secondCreated = secondRequest.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(firstCreated ^ secondCreated).isTrue();
            assertThat(friendshipRepository.count()).isEqualTo(1);
        }
    }

    private boolean requestFriend(
            Member sender,
            Member recipient,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException  {
        ready.countDown();
        if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("친구 요청 시작 신호를 받지 못했습니다.");
        }
        try {
            friendshipService.requestFollow(sender.getMemberId(), recipient.getMemberId());
            return true;
        } catch (FriendshipAlreadyExistsException exception) {
            return false;
        }
    }
}
