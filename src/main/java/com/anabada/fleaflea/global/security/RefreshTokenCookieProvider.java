package com.anabada.fleaflea.global.security;

import com.anabada.fleaflea.domain.refreshtoken.exception.RefreshTokenExpiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieProvider {
    private static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final JwtTokenProvider jwtTokenProvider;

    public ResponseCookie create(String refreshToken) {

        Duration maxAge = jwtTokenProvider.getRemainingValidity(refreshToken);

        if (maxAge.isNegative() || maxAge.isZero()) {

            throw new RefreshTokenExpiredException();
        }

        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie delete() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
