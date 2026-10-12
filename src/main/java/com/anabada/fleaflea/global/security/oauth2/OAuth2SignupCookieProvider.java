package com.anabada.fleaflea.global.security.oauth2;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OAuth2SignupCookieProvider {

    public static final String COOKIE_NAME =
            "oauth2_signup_ticket";

    public void addCookie(
            HttpServletResponse response,
            String ticket
    ) {
        ResponseCookie cookie = ResponseCookie
                .from(COOKIE_NAME, ticket)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/v1/auth/oauth2/signup")
                .maxAge(Duration.ofMinutes(10))
                .build();

        response.addHeader(
                "Set-Cookie",
                cookie.toString()
        );
    }

    public void deleteCookie(
            HttpServletResponse response
    ) {
        ResponseCookie cookie = ResponseCookie
                .from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/v1/auth/oauth2/signup")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(
                "Set-Cookie",
                cookie.toString()
        );
    }
}