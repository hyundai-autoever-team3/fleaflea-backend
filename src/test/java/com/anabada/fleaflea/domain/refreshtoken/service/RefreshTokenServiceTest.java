package com.anabada.fleaflea.domain.refreshtoken.service;

import com.anabada.fleaflea.domain.refreshtoken.exception.InvalidTokenException;
import com.anabada.fleaflea.domain.refreshtoken.exception.RefreshTokenStorageUnavailableException;
import com.anabada.fleaflea.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import com.anabada.fleaflea.fixture.RefreshTokenFixture;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RedisRefreshTokenRepository redisRefreshTokenRepository;

    private JwtTokenProvider jwtTokenProvider;
    private RefreshTokenService refreshTokenService;
    private String refreshToken;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = RefreshTokenFixture.createJwtTokenProvider();
        refreshTokenService = new RefreshTokenService(jwtTokenProvider, redisRefreshTokenRepository);
        refreshToken = jwtTokenProvider.createRefreshToken(1L);
        sessionId = UUID.fromString(jwtTokenProvider.getClaims(refreshToken).getId());
    }

    @Test
    @DisplayName("토큰 원문 대신 SHA-256 해시와 JWT 만료시간을 저장한다")
    void saveRefreshToken_withValidToken_storesHashAndExpiration() {
        when(redisRefreshTokenRepository.saveSession(eq(1L), eq(sessionId), any(), any())).thenReturn(true);

        refreshTokenService.saveRefreshToken(1L, refreshToken);

        ArgumentCaptor<String> tokenHashCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisRefreshTokenRepository).saveSession(
                eq(1L), eq(sessionId), tokenHashCaptor.capture(),
                eq(jwtTokenProvider.getExpiration(refreshToken).toInstant())
        );
        assertThat(tokenHashCaptor.getValue()).matches("[0-9a-f]{64}").isNotEqualTo(refreshToken);
    }

    @Test
    @DisplayName("저장된 해시가 일치하는 세션만 재발급에 사용할 수 있다")
    void getMemberIdFromValidSession_withSavedToken_returnsMemberId() {
        ArgumentCaptor<String> tokenHashCaptor = ArgumentCaptor.forClass(String.class);
        when(redisRefreshTokenRepository.saveSession(eq(1L), eq(sessionId), any(), any())).thenReturn(true);
        refreshTokenService.saveRefreshToken(1L, refreshToken);
        verify(redisRefreshTokenRepository).saveSession(eq(1L), eq(sessionId), tokenHashCaptor.capture(), any());
        when(redisRefreshTokenRepository.findTokenHash(1L, sessionId)).thenReturn(Optional.of(tokenHashCaptor.getValue()));

        assertThat(refreshTokenService.getMemberIdFromValidSession(refreshToken)).isEqualTo(1L);
    }

    @Test
    @DisplayName("해시가 다르거나 삭제된 세션은 재발급할 수 없다")
    void getMemberIdFromValidSession_withDifferentOrMissingHash_throwsInvalidToken() {
        when(redisRefreshTokenRepository.findTokenHash(1L, sessionId))
                .thenReturn(Optional.of("different-hash"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.getMemberIdFromValidSession(refreshToken))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.getMemberIdFromValidSession(refreshToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "invalid-token"})
    @DisplayName("누락되거나 잘못된 토큰은 Redis 조회 전에 거부한다")
    void getMemberIdFromValidSession_withInvalidToken_throwsInvalidToken(String invalidToken) {
        assertThatThrownBy(() -> refreshTokenService.getMemberIdFromValidSession(invalidToken))
                .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(redisRefreshTokenRepository);
    }

    @Test
    @DisplayName("Access Token과 세션 ID 없는 기존 토큰과 만료된 토큰은 거부한다")
    void getMemberIdFromValidSession_withWrongTypeOrLegacyOrExpiredToken_throwsInvalidToken() {
        String accessToken = jwtTokenProvider.createAccessToken(1L);
        String legacyToken = RefreshTokenFixture.createRefreshToken(1L, null, Instant.now().plusSeconds(60));
        String expiredToken = RefreshTokenFixture.createRefreshToken(1L, UUID.randomUUID(), Instant.now().minusSeconds(1));

        for (String invalidToken : new String[]{accessToken, legacyToken, expiredToken}) {
            assertThatThrownBy(() -> refreshTokenService.getMemberIdFromValidSession(invalidToken))
                    .isInstanceOf(InvalidTokenException.class);
        }
        verifyNoInteractions(redisRefreshTokenRepository);
    }

    @Test
    @DisplayName("다른 회원의 토큰으로 세션을 저장하거나 로그아웃할 수 없다")
    void manageSession_withAnotherMemberToken_throwsInvalidToken() {
        assertThatThrownBy(() -> refreshTokenService.saveRefreshToken(2L, refreshToken))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.deleteSession(2L, refreshToken))
                .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(redisRefreshTokenRepository);
    }

    @Test
    @DisplayName("현재 세션 로그아웃은 해당 세션만 삭제한다")
    void deleteSession_withValidToken_deletesOnlyCurrentSession() {
        refreshTokenService.deleteSession(1L, refreshToken);

        verify(redisRefreshTokenRepository).deleteSession(1L, sessionId);
    }

    @Test
    @DisplayName("쿠키 없는 로그아웃은 다른 기기의 세션을 삭제하지 않는다")
    void deleteSession_withoutCookie_preservesOtherSessions() {
        refreshTokenService.deleteSession(1L, null);

        verifyNoInteractions(redisRefreshTokenRepository);
    }

    @Test
    @DisplayName("Redis 조회 실패는 토큰 오류가 아닌 저장소 장애로 구분한다")
    void getMemberIdFromValidSession_withUnavailableRedis_throwsStorageUnavailable() {
        RedisConnectionFailureException connectionFailure = new RedisConnectionFailureException("unavailable");
        when(redisRefreshTokenRepository.findTokenHash(1L, sessionId)).thenThrow(connectionFailure);

        assertThatThrownBy(() -> refreshTokenService.getMemberIdFromValidSession(refreshToken))
                .isInstanceOf(RefreshTokenStorageUnavailableException.class)
                .hasCause(connectionFailure)
                .extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Redis 저장과 세션 삭제 실패를 성공으로 처리하지 않는다")
    void manageSession_withUnavailableRedis_throwsStorageUnavailable() {
        RedisConnectionFailureException connectionFailure = new RedisConnectionFailureException("unavailable");
        when(redisRefreshTokenRepository.saveSession(eq(1L), eq(sessionId), any(), any())).thenThrow(connectionFailure);
        doThrow(connectionFailure).when(redisRefreshTokenRepository).deleteSession(1L, sessionId);
        doThrow(connectionFailure).when(redisRefreshTokenRepository).deleteAllSessions(1L);

        assertThatThrownBy(() -> refreshTokenService.saveRefreshToken(1L, refreshToken))
                .isInstanceOf(RefreshTokenStorageUnavailableException.class);
        assertThatThrownBy(() -> refreshTokenService.deleteSession(1L, refreshToken))
                .isInstanceOf(RefreshTokenStorageUnavailableException.class);
        assertThatThrownBy(() -> refreshTokenService.deleteAllSessions(1L))
                .isInstanceOf(RefreshTokenStorageUnavailableException.class);
    }
}
