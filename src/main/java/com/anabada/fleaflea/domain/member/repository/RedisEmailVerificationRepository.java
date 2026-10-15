package com.anabada.fleaflea.domain.member.repository;

import com.anabada.fleaflea.domain.member.domain.EmailVerificationPurpose;
import com.anabada.fleaflea.domain.member.exception.EmailResendCooldownException;
import com.anabada.fleaflea.domain.member.exception.EmailSendLimitExceededException;
import com.anabada.fleaflea.domain.member.exception.EmailVerificationResultException;
import com.anabada.fleaflea.domain.member.exception.InvalidEmailVerificationTtlException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.anabada.fleaflea.domain.member.redis.EmailVerificationScripts.*;

@Repository
@RequiredArgsConstructor
public class RedisEmailVerificationRepository {

    private static final String KEY_PREFIX = "fleaflea:email-code:";
    private static final String VERIFIED_KEY_PREFIX = "fleaflea:email-verified:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);
    private static final Duration VERIFY_WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private static final int MAX_SEND_COUNT = 5;
    private static final int MAX_VERIFY_COUNT = 10;

    public void save(
            String email,
            String purpose,
            String codeHash,
            Duration ttl
    ) {
        validateTtl(ttl);

        Long result = redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(
                        key(email, purpose),
                        limitKey("send-cooldown", email),
                        limitKey("send-count", email)
                ),
                codeHash,
                Long.toString(ttl.toMillis()),
                Long.toString(RESEND_COOLDOWN.toMillis()),
                Integer.toString(MAX_SEND_COUNT),
                Long.toString(SEND_WINDOW.toMillis())
        );

        if (result == null) {
            throw new EmailVerificationResultException();
        }

        switch (result.intValue()) {
            case 1 -> {
                // 저장 완료
            }
            case -1 -> throw new EmailResendCooldownException();
            case -2 -> throw new EmailSendLimitExceededException();
            default -> throw new EmailVerificationResultException();
        }
    }

    public VerificationResult verifyAndConsume(
            String email,
            String purpose,
            String codeHash
    ) {
        Long result = redisTemplate.execute(
                VERIFY_SCRIPT,
                List.of(
                        key(email, purpose),
                        limitKey("verify-count", email)
                ),
                codeHash,
                Integer.toString(MAX_ATTEMPTS),
                Integer.toString(MAX_VERIFY_COUNT),
                Long.toString(VERIFY_WINDOW.toMillis())
        );

        if (result == null) {
            throw new EmailVerificationResultException();
        }

        return switch (result.intValue()) {
            case 1 -> VerificationResult.SUCCESS;
            case 0 -> VerificationResult.NOT_FOUND;
            case -1 -> VerificationResult.MISMATCH;
            case -2 -> VerificationResult.ATTEMPTS_EXCEED;
            case -3 -> VerificationResult.RATE_LIMIT_EXCEEDED;
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

    private String limitKey(String type, String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return "fleaflea:email-limit:" + type + ":" + normalized;
    }

    public enum VerificationResult {
        SUCCESS,
        NOT_FOUND,
        MISMATCH,
        ATTEMPTS_EXCEED,
        RATE_LIMIT_EXCEEDED
    }




}



