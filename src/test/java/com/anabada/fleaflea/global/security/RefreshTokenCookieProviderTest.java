package com.anabada.fleaflea.global.security;

import com.anabada.fleaflea.fixture.RefreshTokenFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieProviderTest {

    @Test
    @DisplayName("같은 회원에게 발급된 Refresh Token은 서로 다른 세션 ID를 가진다")
    void createRefreshToken_forSameMember_createsUniqueSessions() {
        JwtTokenProvider jwtTokenProvider = RefreshTokenFixture.createJwtTokenProvider();
        String firstRefreshToken = jwtTokenProvider.createRefreshToken(1L);
        String secondRefreshToken = jwtTokenProvider.createRefreshToken(1L);

        assertThat(firstRefreshToken).isNotEqualTo(secondRefreshToken);
        UUID firstSessionId = UUID.fromString(jwtTokenProvider.getClaims(firstRefreshToken).getId());
        UUID secondSessionId = UUID.fromString(jwtTokenProvider.getClaims(secondRefreshToken).getId());
        assertThat(firstSessionId).isNotEqualTo(secondSessionId);
    }

    @Test
    @DisplayName("쿠키 만료시간은 고정된 7일이 아닌 JWT 설정을 따른다")
    void createCookie_withConfiguredExpiration_matchesTokenLifetime() {
        JwtTokenProvider jwtTokenProvider = RefreshTokenFixture.createJwtTokenProvider();
        RefreshTokenCookieProvider refreshTokenCookieProvider = new RefreshTokenCookieProvider(jwtTokenProvider);
        String refreshToken = jwtTokenProvider.createRefreshToken(1L);

        ResponseCookie responseCookie = refreshTokenCookieProvider.create(refreshToken);

        assertThat(responseCookie.getMaxAge()).isBetween(Duration.ofSeconds(298), Duration.ofSeconds(300));
        assertThat(responseCookie.getValue()).isEqualTo(refreshToken);
        assertThat(responseCookie.isHttpOnly()).isTrue();
        assertThat(responseCookie.isSecure()).isTrue();
        assertThat(responseCookie.getSameSite()).isEqualTo("None");
        assertThat(responseCookie.getPath()).isEqualTo("/api/v1/auth");
    }
}
