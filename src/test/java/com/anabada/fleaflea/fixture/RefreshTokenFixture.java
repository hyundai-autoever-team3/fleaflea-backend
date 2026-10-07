package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.global.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RefreshTokenFixture {

    public static final String JWT_SECRET = "refresh-token-test-secret-at-least-32-bytes";

    public static JwtTokenProvider createJwtTokenProvider() {
        return new JwtTokenProvider(JWT_SECRET, 60_000L, 300_000L);
    }

    public static String createRefreshToken(Long memberId, UUID sessionId, Instant expiresAt) {
        return Jwts.builder()
                .subject(memberId.toString())
                .id(sessionId == null ? null : sessionId.toString())
                .claim("type", "REFRESH")
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
