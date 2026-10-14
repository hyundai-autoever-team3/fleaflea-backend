package com.anabada.fleaflea.domain.member.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RedisEmailVerificationRepository {

    private static final String KEY_PREFIX = "fleaflea:email-code:";
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> SAVE_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('DEL', KEYS[1])
                                        redis.call('HSET', KEYS[1],
                                            'codeHash', ARGV[1],
                                            'attempts', '0')
                                        redis.call('PEXPIRE', KEYS[1], ARGV[2])
                                        return 1
                    """, Long.class);

    private static final DefaultRedisScript<Long> VERIFY_SCRIPT =
            new DefaultRedisScript<>("""
                    local stored = redis.call('HGET', KEYS[1], 'codeHash')
                    
                    if not stored then
                    return 0
                    end
                    
                    if stored ~= ARGV[1] then
                        local attempts =
                            redis.call('HINCRBY',KEYS[1], 'attempts', 1)
                    
                            if attempts >= tonumber(ARGV[2]) then
                                redis.call('DEL', KEYS[1])
                                return -2
                            end
                    
                            return -1
                        end
                    
                        redis.call('DEL', KEYS[1])
                        return 1
                    """, Long.class
            );

    public void save(
            String email,
            String purpose,
            String codeHash,
            Duration ttl
    ) {
        validateTtl(ttl);

        redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(key(email, purpose)),
                codeHash,
                Long.toString(ttl.toMillis())
        );
    }

    public VerificationResult verifyAndConsume(
            String email,
            String purpose,
            String codeHash
    ) {
        Long result = redisTemplate.execute(
                VERIFY_SCRIPT,
                List.of(key(email, purpose)),
                codeHash,
                Integer.toString(MAX_ATTEMPTS)
        );

        if (result == null) {
            //수정 필요
            throw new IllegalStateException("인증 코드 검증 결과가 없습니다");
        }

        return switch (result.intValue()) {
            case 1 -> VerificationResult.SUCCESS;
            case 0 -> VerificationResult.NOT_FOUND;
            case -1 -> VerificationResult.MISMATCH;
            case -2 -> VerificationResult.ATTEMPTS_EXCEED;
            //수정 필요
            default -> throw new IllegalStateException(
                    "알 수 없는 인증 코드 검증 결과"
            );
        };
    }

    private String key(String email, String purpose) {
        return KEY_PREFIX + purpose + ":" + email;
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.toMillis() <= 0) {
            //수정 필요
            throw new IllegalArgumentException("TTL은 0보다 커야합니다.");
        }
    }

    public enum VerificationResult {
        SUCCESS,
        NOT_FOUND,
        MISMATCH,
        ATTEMPTS_EXCEED
    }




}



