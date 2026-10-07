package com.anabada.fleaflea.domain.refreshtoken.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenRepository {

    private static final DefaultRedisScript<Long> SAVE_SESSION_SCRIPT = createSaveSessionScript();

    private final StringRedisTemplate stringRedisTemplate;

    public boolean saveSession(Long memberId, UUID sessionId, String tokenHash, Instant expiresAt) {
        Long result = stringRedisTemplate.execute(
                SAVE_SESSION_SCRIPT,
                List.of(getSessionKey(memberId)),
                sessionId.toString(),
                tokenHash,
                Long.toString(expiresAt.toEpochMilli())
        );

        return Long.valueOf(1).equals(result);
    }

    public Optional<String> findTokenHash(Long memberId, UUID sessionId) {
        return Optional.ofNullable(stringRedisTemplate.<String, String>opsForHash()
                .get(getSessionKey(memberId), sessionId.toString()));
    }

    public void deleteSession(Long memberId, UUID sessionId) {
        stringRedisTemplate.opsForHash().delete(getSessionKey(memberId), sessionId.toString());
    }

    public void deleteAllSessions(Long memberId) {
        stringRedisTemplate.delete(getSessionKey(memberId));
    }

    private String getSessionKey(Long memberId) {
        return "auth:refresh:sessions:{" + memberId + "}";
    }

    private static DefaultRedisScript<Long> createSaveSessionScript() {
        DefaultRedisScript<Long> saveSessionScript = new DefaultRedisScript<>();
        saveSessionScript.setLocation(new ClassPathResource("redis/save-refresh-token-session.lua"));
        saveSessionScript.setResultType(Long.class);
        return saveSessionScript;
    }
}
