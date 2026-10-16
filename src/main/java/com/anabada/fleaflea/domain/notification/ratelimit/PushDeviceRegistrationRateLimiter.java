package com.anabada.fleaflea.domain.notification.ratelimit;

import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationRateLimitExceededException;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PushDeviceRegistrationRateLimiter {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local window = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], now, ARGV[3])
            redis.call('PEXPIRE', KEYS[1], window)
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void acquire(Long memberId) {
        Long result;
        try {
            // Redis 시각과 원자적 스크립트로 서버 간 시각 차이·동시 요청을 처리한다.
            // 등록이 DB에서 실패해도 이 요청 기록은 되돌리지 않는다.
            result = redisTemplate.execute(ACQUIRE_SCRIPT,
                    List.of("fleaflea:push:registration-rate:" + memberId),
                    "60000", "10", UUID.randomUUID().toString());
        } catch (DataAccessException exception) {
            throw new PushDeviceRegistrationUnavailableException();
        }
        if (Long.valueOf(0).equals(result)) {
            throw new PushDeviceRegistrationRateLimitExceededException();
        }
        if (!Long.valueOf(1).equals(result)) {
            throw new PushDeviceRegistrationUnavailableException();
        }
    }
}
