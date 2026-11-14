package com.anabada.fleaflea.domain.notification.ratelimit;

import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationRateLimitExceededException;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PushDeviceRegistrationRateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final PushDeviceRegistrationRateLimiter limiter = new PushDeviceRegistrationRateLimiter(redis);

    @Test
    @DisplayName("Redis가 요청을 허용하면 등록을 진행한다")
    void acquire_allowsAcceptedRequest() {
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis가 요청 한도 초과를 반환하면 429 도메인 예외로 거절한다")
    void acquire_rejectsExceededLimit() {
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(PushDeviceRegistrationRateLimitExceededException.class);
    }

    @Test
    @DisplayName("Redis 연결 실패 시 메모리 제한으로 우회하지 않고 등록을 거절한다")
    void acquire_rejectsRedisFailure() {
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("unavailable"));

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(PushDeviceRegistrationUnavailableException.class);
    }

    @Test
    @DisplayName("Redis가 유효한 실행 결과를 반환하지 않으면 등록을 거절한다")
    void acquire_rejectsMissingResult() {
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(PushDeviceRegistrationUnavailableException.class);
    }
}
