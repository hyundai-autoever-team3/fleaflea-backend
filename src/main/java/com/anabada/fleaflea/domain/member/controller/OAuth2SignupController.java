package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.OAuth2SignupRequest;
import com.anabada.fleaflea.domain.member.dto.OAuth2SignupResponse;
import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.service.OAuth2SignupService;
import com.anabada.fleaflea.global.security.RefreshTokenCookieProvider;
import com.anabada.fleaflea.global.security.oauth2.OAuth2SignupCookieProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Parameter;

@Tag(name = "OAuth 인증", description = "카카오 인증 후 추가 회원가입")
@RestController
@RequestMapping("/api/v1/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2SignupController {

    private final OAuth2SignupService oAuth2SignupService;
    private final OAuth2SignupCookieProvider oAuth2SignupCookieProvider;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/signup")
    @Operation(
            summary = "카카오 회원가입 완료",
            description = """
                카카오 인증을 완료한 신규 사용자가 닉네임을 등록합니다.

                사전 조건:
                1. 같은 브라우저에서 /oauth2/authorization/kakao에 접속합니다.
                2. 카카오 로그인과 동의를 완료합니다.
                3. /oauth/signup으로 이동했다면 이 API를 호출합니다.

                브라우저에 저장된 oauth2_signup_ticket 쿠키를 사용합니다.
                쿠키는 브라우저가 자동으로 전송하므로 직접 입력하지 않습니다.
                가입 티켓은 10분 동안 유효하며 한 번만 사용할 수 있습니다.

                성공하면 Access Token을 응답 본문으로 반환하고,
                Refresh Token을 HttpOnly 쿠키로 설정합니다.
                사용한 가입 티켓 쿠키는 삭제합니다.

                현재 구현에서는 닉네임 중복 등으로 가입에 실패해도
                티켓이 이미 소비될 수 있으므로 카카오 로그인을 다시 진행하세요.
                """,
            security = {}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "회원가입 완료 및 토큰 발급"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "닉네임 입력 오류 또는 가입 티켓 쿠키 누락"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "가입 티켓이 만료되었거나 유효하지 않음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 가입된 계정 또는 이메일·닉네임 중복"
            )
    })
    public ResponseEntity<OAuth2SignupResponse> signup(
            @Parameter(hidden = true)
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
