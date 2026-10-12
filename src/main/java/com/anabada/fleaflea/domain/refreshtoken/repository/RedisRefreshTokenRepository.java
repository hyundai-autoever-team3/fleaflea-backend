package com.anabada.fleaflea.domain.refreshtoken.repository;

import com.anabada.fleaflea.domain.refreshtoken.exception.InvalidRefreshTokenTtlException;
import com.anabada.fleaflea.domain.refreshtoken.exception.TokenHashingException;
import com.anabada.fleaflea.domain.refreshtoken.redis.RefreshTokenRotationScript;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenRepository {

    private static final String KEY_PREFIX = "fleaflea:refresh-token:";

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenRotationScript refreshTokenRotationScript;

    public void save(Long memberId, String refreshToken, Duration ttl) {
        validateTtl(ttl);

        redisTemplate.opsForValue().set(
                key(memberId),
                hash(refreshToken),
                ttl
        );
    }

    public boolean rotate(
            Long memberId,
            String oldRefreshToken,
            String newRefreshToken,
            Duration ttl
    ) {
        validateTtl(ttl);

        Long result = redisTemplate.execute(
                refreshTokenRotationScript,
                List.of(key(memberId)),
                hash(oldRefreshToken),
                hash(newRefreshToken),
                Long.toString(ttl.toMillis())
        );

        return Long.valueOf(1L).equals(result);
    }

    public void deleteByMemberId(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    public String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.toMillis() <= 0) {
            throw new InvalidRefreshTokenTtlException();
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new TokenHashingException(exception);
        }
    }


}
