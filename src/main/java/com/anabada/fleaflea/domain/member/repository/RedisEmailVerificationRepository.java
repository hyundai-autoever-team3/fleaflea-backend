package com.anabada.fleaflea.domain.member.repository;

import com.anabada.fleaflea.domain.member.domain.EmailVerificationPurpose;
import com.anabada.fleaflea.domain.member.exception.EmailVerificationResultException;
import com.anabada.fleaflea.domain.member.exception.InvalidEmailVerificationTtlException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisEmailVerificationRepository {

    private static final String KEY_PREFIX = "fleaflea:email-code:";
    private static final String VERIFIED_KEY_PREFIX = "fleaflea:email-verified:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);

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

    private static final DefaultRedisScript<Long> CONSUME_TOKEN_SCRIPT =
            new DefaultRedisScript<>("""
                    local stored = redis.call('GET', KEYS[1])

                    if not stored or stored ~= ARGV[1] then
                        return 0
                    end

                    redis.call('DEL', KEYS[1])
                    return 1
                    """, Long.class);

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
            throw new EmailVerificationResultException();
        }

        return switch (result.intValue()) {
            case 1 -> VerificationResult.SUCCESS;
            case 0 -> VerificationResult.NOT_FOUND;
            case -1 -> VerificationResult.MISMATCH;
            case -2 -> VerificationResult.ATTEMPTS_EXCEED;
            default -> throw new EmailVerificationResultException();
        };
    }

    public String issueVerificationToken(
            String email,
            EmailVerificationPurpose purpose
    ) {
        String token = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                VERIFIED_KEY_PREFIX + token,
                purpose.name() + ":" + email,
                VERIFIED_TTL
        );
        return token;
    }

    public boolean consumeVerificationToken(
            String token,
            String email,
            EmailVerificationPurpose purpose
    ) {
        if (token == null || token.isBlank()) {
            return false;
        }

        Long result = redisTemplate.execute(
                CONSUME_TOKEN_SCRIPT,
                List.of(VERIFIED_KEY_PREFIX + token),
                purpose.name() + ":" + email
        );

        if (result == null || (result != 0L && result != 1L)) {
            throw new EmailVerificationResultException();
        }

        return result == 1L;
    }

    private String key(String email, String purpose) {
        return KEY_PREFIX + purpose + ":" + email;
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.toMillis() <= 0) {
            throw new InvalidEmailVerificationTtlException();
        }
    }

    public enum VerificationResult {
        SUCCESS,
        NOT_FOUND,
        MISMATCH,
        ATTEMPTS_EXCEED
    }




}



