package com.anabada.fleaflea.domain.refreshtoken.service;

import com.anabada.fleaflea.domain.refreshtoken.exception.InvalidTokenException;
import com.anabada.fleaflea.domain.refreshtoken.exception.RefreshTokenStorageUnavailableException;
import com.anabada.fleaflea.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenRepository redisRefreshTokenRepository;

    public void saveRefreshToken(Long memberId, String refreshToken) {
        RefreshTokenSession refreshTokenSession = getRefreshTokenSession(refreshToken);
        validateSessionOwner(memberId, refreshTokenSession);

        try {
            boolean saved = redisRefreshTokenRepository.saveSession(
                    memberId,
                    refreshTokenSession.sessionId(),
                    hashRefreshToken(refreshToken),
                    refreshTokenSession.expiresAt()
            );
            if (!saved) {
                throw new InvalidTokenException();
            }
        } catch (DataAccessException exception) {
            throw new RefreshTokenStorageUnavailableException(exception);
        }
    }

    public Long getMemberIdFromValidSession(String refreshToken) {
        RefreshTokenSession refreshTokenSession = getRefreshTokenSession(refreshToken);

        try {
            String savedTokenHash = redisRefreshTokenRepository.findTokenHash(
                    refreshTokenSession.memberId(), refreshTokenSession.sessionId()
            ).orElseThrow(InvalidTokenException::new);

            if (!MessageDigest.isEqual(
                    savedTokenHash.getBytes(StandardCharsets.UTF_8),
                    hashRefreshToken(refreshToken).getBytes(StandardCharsets.UTF_8)
            )) {
                throw new InvalidTokenException();
            }
            return refreshTokenSession.memberId();
        } catch (DataAccessException exception) {
            throw new RefreshTokenStorageUnavailableException(exception);
        }
    }

    public void deleteSession(Long memberId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        RefreshTokenSession refreshTokenSession = getRefreshTokenSession(refreshToken);
        validateSessionOwner(memberId, refreshTokenSession);

        try {
            redisRefreshTokenRepository.deleteSession(memberId, refreshTokenSession.sessionId());
        } catch (DataAccessException exception) {
            throw new RefreshTokenStorageUnavailableException(exception);
        }
    }

    public void deleteAllSessions(Long memberId) {
        try {
            redisRefreshTokenRepository.deleteAllSessions(memberId);
        } catch (DataAccessException exception) {
            throw new RefreshTokenStorageUnavailableException(exception);
        }
    }

    private RefreshTokenSession getRefreshTokenSession(String refreshToken) {
        try {
            Claims claims = jwtTokenProvider.getClaims(refreshToken);
            if (!"REFRESH".equals(claims.get("type", String.class))
                    || claims.getId() == null || claims.getExpiration() == null) {
                throw new InvalidTokenException();
            }

            return new RefreshTokenSession(
                    Long.valueOf(claims.getSubject()),
                    UUID.fromString(claims.getId()),
                    claims.getExpiration().toInstant()
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException();
        }
    }

    private void validateSessionOwner(Long memberId, RefreshTokenSession refreshTokenSession) {
        if (!memberId.equals(refreshTokenSession.memberId())) {
            throw new InvalidTokenException();
        }
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            byte[] tokenHash = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(tokenHash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record RefreshTokenSession(Long memberId, UUID sessionId, Instant expiresAt) {
    }
}
