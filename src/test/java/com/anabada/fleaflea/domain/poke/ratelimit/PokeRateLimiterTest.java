package com.anabada.fleaflea.domain.poke.ratelimit;

import com.anabada.fleaflea.domain.poke.exception.PokeLimitExceededException;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokeRateLimiterTest {

    private static final Long SENDER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long OTHER_RECIPIENT_ID = 3L;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private MutableClock clock;
    private PokeRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(
                Instant.parse("2026-09-22T14:59:59Z"),
                KOREA_ZONE
        );
        limiter = new PokeRateLimiter(
                clock,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofDays(2))
                        .build()
        );
    }

    @Test
    @DisplayName("같은 수신자에게 하루 다섯 번까지 콕찌르기를 보낼 수 있다")
    void allowsFivePokesPerDay() {
        acquireDailyLimit(RECIPIENT_ID);

        assertThatThrownBy(() -> limiter.acquire(SENDER_ID, RECIPIENT_ID))
                .isInstanceOf(PokeLimitExceededException.class);
    }

    @Test
    @DisplayName("수신자가 다르면 콕찌르기 횟수를 별도로 계산한다")
    void countsEachRecipientSeparately() {
        acquireDailyLimit(RECIPIENT_ID);

        limiter.acquire(SENDER_ID, OTHER_RECIPIENT_ID);
    }

    @Test
    @DisplayName("한국 시간 자정이 지나면 콕찌르기 횟수를 초기화한다")
    void resetsCountAtMidnightInKorea() {
        acquireDailyLimit(RECIPIENT_ID);

        clock.advance(Duration.ofSeconds(1));

        limiter.acquire(SENDER_ID, RECIPIENT_ID);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 사용한 콕찌르기 횟수를 복구한다")
    void restoresCountAfterRollback() {
        TransactionSynchronizationManager.initSynchronization();
        List<TransactionSynchronization> synchronizations;
        try {
            limiter.acquire(SENDER_ID, RECIPIENT_ID);
            synchronizations = TransactionSynchronizationManager.getSynchronizations();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        );

        acquireDailyLimit(RECIPIENT_ID);

        assertThatThrownBy(() -> limiter.acquire(SENDER_ID, RECIPIENT_ID))
                .isInstanceOf(PokeLimitExceededException.class);
    }

    @Test
    @DisplayName("동시에 요청해도 같은 수신자에게 다섯 번만 콕찌르기를 허용한다")
    void allowsOnlyFiveConcurrentRequests() throws Exception {
        int requestCount = 50;
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Void>> requests = IntStream.range(0, requestCount)
                .mapToObj(ignored -> (Callable<Void>) () -> {
                    start.await();
                    try {
                        limiter.acquire(SENDER_ID, RECIPIENT_ID);
                        successCount.incrementAndGet();
                    } catch (PokeLimitExceededException exception) {
                        rejectedCount.incrementAndGet();
                    }
                    return null;
                })
                .toList();

        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<Future<Void>> futures = requests.stream()
                    .map(executor::submit)
                    .toList();

            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        }

        assertThat(successCount).hasValue(PokeRateLimiter.DAILY_LIMIT);
        assertThat(rejectedCount).hasValue(requestCount - PokeRateLimiter.DAILY_LIMIT);
    }

    private void acquireDailyLimit(Long recipientId) {
        for (int count = 0; count < PokeRateLimiter.DAILY_LIMIT; count++) {
            limiter.acquire(SENDER_ID, recipientId);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
