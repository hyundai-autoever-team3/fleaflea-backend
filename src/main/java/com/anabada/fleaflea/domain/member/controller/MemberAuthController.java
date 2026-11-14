package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.*;
import com.anabada.fleaflea.domain.member.service.MemberAuthService;
import com.anabada.fleaflea.domain.member.service.MemberService;
import com.anabada.fleaflea.domain.refreshtoken.dto.ReissueResponse;
import com.anabada.fleaflea.global.security.RefreshTokenCookieProvider;
import com.anabada.fleaflea.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final MemberService memberService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = """
            SIGN_UP 목적으로 이메일 코드 발송 및 검증을 먼저 완료하세요.
            /api/v1/auth/email/verify 응답의 verificationToken을 요청의 verificationToken 필드에 전달합니다.
            토큰은 인증한 이메일과 목적이 일치해야 하며, 10분간 유효하고 한 번만 사용할 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 인증 토큰 만료·소비·이메일/목적 불일치"),
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
    @Operation(summary = "토큰 재발급", description = """
        HttpOnly 쿠키의 Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다.
        Access Token은 응답 body로, Refresh Token은 HttpOnly 쿠키로 전달합니다.
        재발급에 사용된 기존 Refresh Token은 즉시 무효화됩니다.
        카카오·구글 기존 회원 로그인 후 /oauth/success로 이동했다면 이 API를 호출하세요.
        Access Token 없이 호출할 수 있습니다.
        """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access Token 재발급 성공",
                    content = @Content(schema = @Schema(implementation = ReissueResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token이 유효하지 않음")
    })
    public ResponseEntity<ReissueResponse> reissue(
            @Parameter(hidden = true)
            @CookieValue(
            name = "refresh_token",
            required = false
    ) String refreshToken
    ) {
        TokenPair tokens = memberAuthService.reissue(refreshToken);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider
                                .create(tokens.refreshToken())
                                .toString()
                )
                .body(new ReissueResponse(tokens.accessToken()));
    }

    @DeleteMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 로그인한 사용자의 Refresh Token을 삭제하여 로그아웃합니다. 요청 본문의 deviceId를 지정하면 본인 기기의 푸시 등록만 해제하며, 본문 또는 deviceId를 생략하면 푸시 등록을 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 기기 ID"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "다른 회원의 기기"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원")
    })
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody(required = false) LogoutRequest request
    ) {
        memberAuthService.logout(memberId, request);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.delete().toString()
                )
                .build();
    }

    @Operation(summary = "이메일 인증으로 비밀번호 재설정", description = """
            로그인 없이 비밀번호를 재설정합니다. PASSWORD_RESET 목적으로 이메일 코드 검증을 완료한 뒤
            응답의 verificationToken과 같은 이메일, 새 비밀번호(8~20자)를 전달하세요.
            토큰은 10분간 유효하고 한 번만 사용할 수 있으며, 소셜 계정은 재설정할 수 없습니다.
            성공하면 기존 Refresh Token을 삭제하고 요청 브라우저의 refresh_token 쿠키를 만료시킵니다.
            기존 Access Token은 만료 전까지 유효합니다. 재설정 후 새 비밀번호로 로그인하세요.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "비밀번호 재설정 성공", content = @Content),
            @ApiResponse(responseCode = "400", description = "잘못된 요청, 유효하지 않은 인증 토큰 또는 소셜 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
            ) {
        memberService.resetPassword(request);

        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.delete().toString()
                )
                .build();
    }

}
