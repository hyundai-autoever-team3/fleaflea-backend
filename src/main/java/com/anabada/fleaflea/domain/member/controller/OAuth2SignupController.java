package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.OAuth2SignupRequest;
import com.anabada.fleaflea.domain.member.dto.OAuth2SignupResponse;
import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.service.OAuth2SignupService;
import com.anabada.fleaflea.global.security.RefreshTokenCookieProvider;
import com.anabada.fleaflea.global.security.oauth2.OAuth2SignupCookieProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2SignupController {

    private final OAuth2SignupService oAuth2SignupService;
    private final OAuth2SignupCookieProvider oAuth2SignupCookieProvider;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/signup")
    public ResponseEntity<OAuth2SignupResponse> signup(
            @CookieValue(
                    name = OAuth2SignupCookieProvider.COOKIE_NAME
            )
            String signupTicket,
            @Valid @RequestBody OAuth2SignupRequest request,
            HttpServletResponse response
    ) {
        TokenPair tokenPair =
                oAuth2SignupService.signup(
                        signupTicket,
                        request
                );

        oAuth2SignupCookieProvider.deleteCookie(response);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider
                        .create(tokenPair.refreshToken())
                        .toString()
        );
        return ResponseEntity.ok(
                new OAuth2SignupResponse(
                        tokenPair.accessToken()
                )
        );
    }
}
