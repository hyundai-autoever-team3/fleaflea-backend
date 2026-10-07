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
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<Void> signUp(
            @Valid @RequestBody
            SignUpRequest signUpRequest
    ) {
        memberAuthService.signUp(signUpRequest);
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
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 올바르지 않음"),
            @ApiResponse(responseCode = "503", description = "인증 세션 저장소를 사용할 수 없음")
    })
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody
            LoginRequest loginRequest
    ) {
        TokenPair tokenPair = memberAuthService.login(loginRequest);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider
                                .create(tokenPair.refreshToken())
                                .toString()
                )
                .body(LoginResponse.from(tokenPair));
    }

    @PostMapping("/reissue")
    @Operation(summary = "Access Token 재발급", description = "HttpOnly Cookie의 Refresh Token을 사용해 새로운 Access Token을 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access Token 재발급 성공",
                    content = @Content(schema = @Schema(implementation = ReissueResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token이 유효하지 않음"),
            @ApiResponse(responseCode = "503", description = "인증 세션 저장소를 사용할 수 없음")
    })
    public ResponseEntity<ReissueResponse> reissue(
            @CookieValue(name = "refresh_token", required = false)
            String refreshToken
    ) {
        return ResponseEntity.ok(memberAuthService.reissue(refreshToken));
    }

    @DeleteMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 기기의 Refresh Token 세션을 삭제합니다. 다른 기기의 세션은 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 또는 Refresh Token이 유효하지 않음"),
            @ApiResponse(responseCode = "503", description = "인증 세션 저장소를 사용할 수 없음")
    })
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal
            Long memberId,

            @CookieValue(name = "refresh_token", required = false)
            String refreshToken
    ) {
        memberAuthService.logout(memberId, refreshToken);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.delete().toString()
                )
                .build();
    }

    @DeleteMapping("/logout/all")
    @Operation(summary = "전체 기기 로그아웃", description = "현재 사용자의 모든 Refresh Token 세션을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "전체 기기 로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "503", description = "인증 세션 저장소를 사용할 수 없음")
    })
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal
            Long memberId
    ) {
        memberAuthService.logoutAll(memberId);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.delete().toString())
                .build();
    }
}
