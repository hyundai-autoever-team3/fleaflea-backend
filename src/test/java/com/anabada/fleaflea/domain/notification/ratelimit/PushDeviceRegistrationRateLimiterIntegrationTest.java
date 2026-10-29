package com.anabada.fleaflea.domain.notification.ratelimit;

import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationRateLimitExceededException;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PushDeviceRegistrationRateLimiterIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PushDeviceRegistrationRateLimiterIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PushDeviceRegistrationRateLimiter limiter;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE - 1);
    }

    @AfterEach
    void tearDown() {
        redis.delete(List.of(key(memberId), key(memberId + 1)));
    }

    @Test
    @DisplayName("다른 서버와 재생성된 제한기도 회원별 요청 기록을 공유하며 만료 시간을 설정한다")
    void acquire_sharesLimitAcrossInstances() {
        for (int index = 0; index < 10; index++)
            limiter.acquire(memberId);
        var anotherInstance = new PushDeviceRegistrationRateLimiter(redis);

        assertThatThrownBy(() -> anotherInstance.acquire(memberId))
                .isInstanceOf(PushDeviceRegistrationRateLimitExceededException.class);
        anotherInstance.acquire(memberId + 1);
        assertThat(redis.opsForZSet().zCard(key(memberId))).isEqualTo(10L);
        assertThat(redis.getExpire(key(memberId), TimeUnit.MILLISECONDS)).isBetween(1L, 60_000L);
    }

    @Test
    @DisplayName("Redis 시각 기준으로 오래된 요청만 제거하고 최근 요청은 한도에 포함한다")
    void acquire_removesOnlyExpiredRequests() {
        Long now = redis.execute(new DefaultRedisScript<>("""
                local time = redis.call('TIME')
                return tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
                """, Long.class), List.of());

        assertThat(now).isNotNull();
        for (int index = 0; index < 9; index++) {
            redis.opsForZSet().add(key(memberId), "old-" + index, now - 61_000);
        }
        redis.opsForZSet().add(key(memberId), "recent", now);

        for (int index = 0; index < 9; index++)
            limiter.acquire(memberId);
        assertThatThrownBy(() -> limiter.acquire(memberId))
                .isInstanceOf(PushDeviceRegistrationRateLimitExceededException.class);
        assertThat(redis.opsForZSet().zCard(key(memberId))).isEqualTo(10L);
    }

    @Test
    @DisplayName("서로 다른 제한기의 병렬 요청도 회원당 정확히 열 번만 허용한다")
    void acquire_concurrentlySharesLimit() throws Exception {
        int requestCount = 20;
        var anotherInstance = new PushDeviceRegistrationRateLimiter(redis);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                var instance = index % 2 == 0 ? limiter : anotherInstance;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS))
                        throw new IllegalStateException("barrier timeout");
                    try {
                        instance.acquire(memberId);
                        return true;
                    } catch (PushDeviceRegistrationRateLimitExceededException exception) {
                        return false;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS))
                    successes++;
            }
            assertThat(successes).isEqualTo(10);
        }
    }

    private String key(Long id) {
        return "fleaflea:push:registration-rate:" + id;
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    @Import({RedisTestContainerConfiguration.class, PushDeviceRegistrationRateLimiter.class})
    static class TestApplication {
    }
}
