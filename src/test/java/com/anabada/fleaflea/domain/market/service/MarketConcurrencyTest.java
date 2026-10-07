package com.anabada.fleaflea.domain.market.service;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.dto.MarketUpdateRequest;
import com.anabada.fleaflea.domain.market.exception.InvalidMarketInviteCodeException;
import com.anabada.fleaflea.domain.market.exception.MarketHostOnlyException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.dto.MarketJoinRequest;
import com.anabada.fleaflea.domain.marketmember.exception.AlreadyJoinedMarketException;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.marketmember.service.MarketJoinService;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MarketMemberFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.ConcurrentModificationException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@Import(RedisTestContainerConfiguration.class)
@Sql(statements = "TRUNCATE TABLE members RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "TRUNCATE TABLE members RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MarketConcurrencyTest {

    @Autowired
    private MarketService marketService;

    @Autowired
    private MarketJoinService marketJoinService;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private MarketMemberRepository marketMemberRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private ThreadPoolTaskScheduler redisLockRenewalScheduler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private Member host;
    private Member participant;
    private Market market;

    @BeforeEach
    void setUp() {
        host = memberRepository.save(MemberFixture.createMember("market-host"));
        participant = memberRepository.save(MemberFixture.createMember("market-participant"));
        Market marketFixture = MarketFixture.createMarket(host);
        marketFixture.changeInviteCode("CONCURRENT");
        market = marketRepository.save(marketFixture);
        marketMemberRepository.save(MarketMemberFixture.createMarketMember(market, host));
    }

    @Test
    @DisplayName("같은 회원이 동시에 참여하면 멤버십은 하나만 생성된다")
    void joinMarket_concurrentRequests_createsOneMembership() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> joinAfterStart(ready, start));
            Future<Boolean> second = executor.submit(() -> joinAfterStart(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(marketMemberRepository.count()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("마켓 이름과 설명을 동시에 수정해도 두 변경이 모두 보존된다")
    void updateMarket_concurrentPartialChanges_preservesBothChanges() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> titleChange = executor.submit(() -> {
                awaitStart(ready, start);
                marketService.updateMarket(host.getMemberId(), market.getMarketId(),
                        new MarketUpdateRequest("changed-title", null, null));
                return null;
            });
            Future<?> descriptionChange = executor.submit(() -> {
                awaitStart(ready, start);
                marketService.updateMarket(host.getMemberId(), market.getMarketId(),
                        new MarketUpdateRequest(null, "changed-description", null));
                return null;
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            titleChange.get(10, TimeUnit.SECONDS);
            descriptionChange.get(10, TimeUnit.SECONDS);

            Market updatedMarket = marketRepository.findById(market.getMarketId()).orElseThrow();
            assertThat(updatedMarket.getTitle()).isEqualTo("changed-title");
            assertThat(updatedMarket.getDescription()).isEqualTo("changed-description");
        }
    }

    @Test
    @DisplayName("초대 코드를 재발급하면 이전 코드로 참여할 수 없다")
    void joinMarket_reissuedInvitation_rejectsOldCode() {
        marketService.reissueInvitation(host.getMemberId(), market.getMarketId());

        assertThatThrownBy(() -> marketJoinService.joinMarket(
                participant.getMemberId(), new MarketJoinRequest("CONCURRENT")
        )).isInstanceOf(InvalidMarketInviteCodeException.class);
        assertThat(marketMemberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("권한 검증이 실패해도 락이 해제되어 개설자가 수정할 수 있다")
    void updateMarket_permissionFailure_releasesLock() {
        assertThatThrownBy(() -> marketService.updateMarket(
                participant.getMemberId(), market.getMarketId(), new MarketUpdateRequest("invalid", null, null)
        )).isInstanceOf(MarketHostOnlyException.class);

        marketService.updateMarket(host.getMemberId(), market.getMarketId(),
                new MarketUpdateRequest("valid", null, null));

        assertThat(marketRepository.findById(market.getMarketId()).orElseThrow().getTitle()).isEqualTo("valid");
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백되기 전에는 다른 Redis 인스턴스가 락을 획득할 수 없다")
    void updateMarket_outerRollback_holdsLockUntilCompletion() throws Exception {
        RedisLockRegistry competingRegistry = new RedisLockRegistry(redisConnectionFactory, "fleaflea:lock", 60000);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                marketService.updateMarket(host.getMemberId(), market.getMarketId(),
                        new MarketUpdateRequest("rolled-back", null, null));
                try {
                    assertThat(executor.submit(() -> tryCompetingLock(competingRegistry))
                            .get(5, TimeUnit.SECONDS)).isFalse();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                status.setRollbackOnly();
            });

            assertThat(executor.submit(() -> tryCompetingLock(competingRegistry))
                    .get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(marketRepository.findById(market.getMarketId()).orElseThrow().getTitle())
                    .isEqualTo(market.getTitle());
        } finally {
            competingRegistry.destroy();
        }
    }

    @Test
    @DisplayName("외부 트랜잭션이 커밋된 뒤에 다른 인스턴스가 락을 획득하고 최신 값을 조회한다")
    void updateMarket_outerCommit_releasesAfterCommit() throws Exception {
        RedisLockRegistry competingRegistry = new RedisLockRegistry(redisConnectionFactory, "fleaflea:lock", 60000);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                marketService.updateMarket(host.getMemberId(), market.getMarketId(),
                        new MarketUpdateRequest("committed", null, null));
                try {
                    assertThat(executor.submit(() -> tryCompetingLock(competingRegistry))
                            .get(5, TimeUnit.SECONDS)).isFalse();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });

            assertThat(executor.submit(() -> {
                Lock lock = competingRegistry.obtain("market:" + market.getMarketId());
                if (!lock.tryLock(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("lock not released");
                }
                try {
                    return marketRepository.findById(market.getMarketId()).orElseThrow().getTitle();
                } finally {
                    lock.unlock();
                }
            }).get(5, TimeUnit.SECONDS)).isEqualTo("committed");
        } finally {
            competingRegistry.destroy();
        }
    }

    private boolean tryCompetingLock(RedisLockRegistry competingRegistry) throws InterruptedException {
        Lock lock = competingRegistry.obtain("market:" + market.getMarketId());
        boolean acquired = lock.tryLock(100, TimeUnit.MILLISECONDS);
        if (acquired) {
            lock.unlock();
        }
        return acquired;
    }

    @Test
    @DisplayName("락이 만료된 소유자는 새 소유자의 락을 해제할 수 없다")
    void unlock_expiredOwner_preservesNewOwnerLock() throws Exception {
        String lockKey = "market:expiry:" + UUID.randomUUID();
        RedisLockRegistry expiredRegistry = new RedisLockRegistry(redisConnectionFactory, "fleaflea:lock", 300);
        RedisLockRegistry newRegistry = new RedisLockRegistry(redisConnectionFactory, "fleaflea:lock", 60000);
        RedisLockRegistry thirdRegistry = new RedisLockRegistry(redisConnectionFactory, "fleaflea:lock", 60000);
        Lock expiredLock = expiredRegistry.obtain(lockKey);
        Lock newLock = newRegistry.obtain(lockKey);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            assertThat(expiredLock.tryLock(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(600);
            assertThat(newLock.tryLock(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(expiredLock::unlock).isInstanceOf(ConcurrentModificationException.class);
            assertThat(executor.submit(() -> {
                Lock thirdLock = thirdRegistry.obtain(lockKey);
                boolean acquired = thirdLock.tryLock(100, TimeUnit.MILLISECONDS);
                if (acquired) {
                    thirdLock.unlock();
                }
                return acquired;
            }).get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            newLock.unlock();
            expiredRegistry.destroy();
            newRegistry.destroy();
            thirdRegistry.destroy();
        }
    }

    @Test
    @DisplayName("작업이 만료시간보다 길어져도 자동 갱신으로 다른 인스턴스의 진입을 막는다")
    void acquireLock_longOperation_renewsLease() throws Exception {
        String lockKey = "market:renewal:" + UUID.randomUUID();
        RedisLockRegistry renewingRegistry = new RedisLockRegistry(redisConnectionFactory, "fleaflea:lock", 1500);
        renewingRegistry.setRenewalTaskScheduler(redisLockRenewalScheduler);
        RedisLockRegistry competingRegistry = new RedisLockRegistry(redisConnectionFactory, "fleaflea:lock", 60000);
        Lock renewingLock = renewingRegistry.obtain(lockKey);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            assertThat(renewingLock.tryLock(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(2200);

            assertThat(executor.submit(() -> {
                Lock competingLock = competingRegistry.obtain(lockKey);
                boolean acquired = competingLock.tryLock(100, TimeUnit.MILLISECONDS);
                if (acquired) {
                    competingLock.unlock();
                }
                return acquired;
            }).get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            renewingLock.unlock();
            renewingRegistry.destroy();
            competingRegistry.destroy();
        }
    }

    private boolean joinAfterStart(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        awaitStart(ready, start);
        try {
            marketJoinService.joinMarket(participant.getMemberId(), new MarketJoinRequest(" concurrent "));
            return true;
        } catch (AlreadyJoinedMarketException exception) {
            return false;
        }
    }

    private void awaitStart(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("concurrent requests did not start");
        }
    }
}
