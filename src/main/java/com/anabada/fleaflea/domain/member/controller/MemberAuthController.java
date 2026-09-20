package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.LoginRequest;
import com.anabada.fleaflea.domain.member.dto.LoginResponse;
import com.anabada.fleaflea.domain.member.dto.SignUpRequest;
import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.service.MemberAuthService;
import com.anabada.fleaflea.domain.refreshtoken.dto.ReissueResponse;
import com.anabada.fleaflea.global.security.RefreshTokenCookieProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증")
public class MemberAuthController {
    private final MemberAuthService memberAuthService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임을 사용하여 회원가입합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음"),
            @ApiResponse(responseCode = "409", description = "이메일 또는 닉네임이 이미 존재함")
    })
    public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        memberAuthService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = """
                이메일과 비밀번호를 검증합니다.
                Access Token은 응답 body로, Refresh Token은 HttpOnly Cookie로 발급합니다.
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 올바르지 않음")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPair tokens = memberAuthService.login(request);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider
                                .create(tokens.refreshToken())
                                .toString()
                )
                .body(new LoginResponse(tokens.accessToken()));
    }

    @PostMapping("/reissue")
    @Operation(summary = "Access Token 재발급", description = "HttpOnly Cookie의 Refresh Token을 사용해 새로운 Access Token을 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access Token 재발급 성공",
                    content = @Content(schema = @Schema(implementation = ReissueResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token이 유효하지 않음")
    })
    public ResponseEntity<ReissueResponse> reissue(
            @CookieValue(
            name = "refresh_token",
            required = false
    ) String refreshToken
    ) {
        return ResponseEntity.ok(memberAuthService.reissue(refreshToken));
    }

    @DeleteMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 로그인한 사용자의 Refresh Token을 삭제하여 로그아웃합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Long memberId
    ) {
        memberAuthService.logout(memberId);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.delete().toString()
                )
                .build();
    }

}
