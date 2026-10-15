package com.anabada.fleaflea.domain.member.redis;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmailVerificationScripts {
    public static final DefaultRedisScript<Long> SAVE_SCRIPT =
            new DefaultRedisScript<>("""
                -- KEYS[1]: 인증 코드
                -- KEYS[2]: 재발송 대기
                -- KEYS[3]: 발송 횟수

                if redis.call('EXISTS', KEYS[2]) == 1 then
                    return -1
                end

                local count = tonumber(redis.call('GET', KEYS[3]) or '0')

                if count >= tonumber(ARGV[4]) then
                    return -2
                end

                local updated = redis.call('INCR', KEYS[3])

                if updated == 1 then
                    redis.call('PEXPIRE', KEYS[3], ARGV[5])
                end

                redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])

                redis.call('DEL', KEYS[1])
                redis.call('HSET', KEYS[1],
                    'codeHash', ARGV[1],
                    'attempts', '0')
                redis.call('PEXPIRE', KEYS[1], ARGV[2])

                return 1
                """, Long.class);

    public static final DefaultRedisScript<Long> VERIFY_SCRIPT =
            new DefaultRedisScript<>("""
                -- KEYS[1]: 인증 코드
                -- KEYS[2]: 이메일별 검증 횟수

                local count = tonumber(redis.call('GET', KEYS[2]) or '0')

                if count >= tonumber(ARGV[3]) then
                    return -3
                end

                local updated = redis.call('INCR', KEYS[2])

                if updated == 1 then
                    redis.call('PEXPIRE', KEYS[2], ARGV[4])
                end

                local stored = redis.call('HGET', KEYS[1], 'codeHash')

                if not stored then
                    return 0
                end

                if stored ~= ARGV[1] then
                    local attempts =
                        redis.call('HINCRBY', KEYS[1], 'attempts', 1)

                    if attempts >= tonumber(ARGV[2]) then
                        redis.call('DEL', KEYS[1])
                        return -2
                    end

                    return -1
                end

                redis.call('DEL', KEYS[1])
                return 1
                """, Long.class);

    public static final DefaultRedisScript<Long> CONSUME_TOKEN_SCRIPT =
            new DefaultRedisScript<>("""
                    local stored = redis.call('GET', KEYS[1])
                    
                    if not stored or stored ~= ARGV[1] then
                        return 0
                    end
                    
                    redis.call('DEL', KEYS[1])
                    return 1
                    """, Long.class);
}
