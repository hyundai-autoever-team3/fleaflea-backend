package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.EmailCodeSendRequest;
import com.anabada.fleaflea.domain.member.dto.EmailCodeVerifyRequest;
import com.anabada.fleaflea.domain.member.dto.EmailCodeVerifyResponse;
import com.anabada.fleaflea.domain.member.service.EmailVerificationService;
import com.anabada.fleaflea.domain.member.repository.RedisEmailVerificationRepository.VerificationResult;
import com.anabada.fleaflea.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/email")
@RequiredArgsConstructor
@Tag(name = "이메일 인증", description = "회원가입 및 비밀번호 재설정을 위한 이메일 인증 API. 로그인 없이 호출할 수 있습니다.")
public class EmailVerificationController {
    private final EmailVerificationService verificationService;

    @Operation(summary = "이메일 인증 코드 발송", description = """
            이메일로 6자리 숫자 코드를 발송합니다. 코드는 5분간 유효합니다.
            회원가입은 SIGN_UP, 비밀번호 재설정은 PASSWORD_RESET을 사용하세요.
            재발송하면 이전 코드는 무효화되고 검증 실패 횟수가 초기화됩니다.
            발송 후 /api/v1/auth/email/verify에서 같은 이메일과 목적으로 검증하세요.
            같은 이메일은 60초 후 재발송할 수 있습니다.
            첫 발송부터 1시간 동안 최대 5회 발송을 시도할 수 있습니다.
            제한은 인증 목적과 관계없이 이메일별로 공유합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "인증 코드 발송 완료", content = @Content),
            @ApiResponse(responseCode = "400", description = "이메일 형식 또는 인증 목적이 올바르지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "재발송 대기 중 또는 시간 내 발송 횟수 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "메일 발송 또는 인증 처리 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/code")
    public ResponseEntity<Void> sendCode(
            @Valid @RequestBody EmailCodeSendRequest request
    ) {
        verificationService.sendCode(
                request.email(),
                request.purpose()
        );

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "이메일 인증 코드 검증", description = """
            발송할 때 사용한 이메일과 목적, 메일로 받은 6자리 코드를 전달합니다.
            성공하면 코드를 소비하고 10분간 유효한 일회용 verificationToken을 반환합니다.
            SIGN_UP 토큰은 /api/v1/auth/signup, PASSWORD_RESET 토큰은
            /api/v1/auth/password/reset 요청에 전달하세요. 다음 API는 클라이언트가 호출해야 합니다.
            코드 불일치가 5회 누적되면 코드가 삭제되므로 새 코드를 발급받아야 합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공 및 일회용 토큰 발급",
                    content = @Content(schema = @Schema(implementation = EmailCodeVerifyResponse.class),
                            examples = @ExampleObject(value = "{\"result\":\"SUCCESS\",\"verificationToken\":\"550e8400-e29b-41d4-a716-446655440000\"}"))),
            @ApiResponse(responseCode = "400", description = "코드 없음·만료·소비됨(NOT_FOUND), 코드 불일치(MISMATCH), 또는 요청 값 오류(INVALID_REQUEST)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(oneOf = {EmailCodeVerifyResponse.class, ErrorResponse.class}),
                            examples = {
                            @ExampleObject(name = "코드 없음", value = "{\"result\":\"NOT_FOUND\",\"verificationToken\":null}"),
                            @ExampleObject(name = "코드 불일치", value = "{\"result\":\"MISMATCH\",\"verificationToken\":null}"),
                            @ExampleObject(name = "잘못된 요청", value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"잘못된 요청입니다.\"}")
                    })),
            @ApiResponse(responseCode = "429", description = "ATTEMPTS_EXCEED: 현재 코드의 불일치 5회 도달, 코드 삭제\n" + "RATE_LIMIT_EXCEEDED: 이메일별 10분 내 검증 요청 10회 소진",
                    content = @Content(schema = @Schema(implementation = EmailCodeVerifyResponse.class),
                            examples = @ExampleObject(value = "{\"result\":\"ATTEMPTS_EXCEED\",\"verificationToken\":null}"))),
            @ApiResponse(responseCode = "500", description = "인증 처리 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/verify")
    public ResponseEntity<EmailCodeVerifyResponse> verifyCode(
            @Valid @RequestBody EmailCodeVerifyRequest request
            ) {
        EmailCodeVerifyResponse response =  verificationService.verifyCode(
                request.email(),
                request.purpose(),
                request.code()
        );

        VerificationResult result = VerificationResult.valueOf(response.result());

        HttpStatus status = switch (result) {
            case SUCCESS -> HttpStatus.OK;
            case NOT_FOUND, MISMATCH -> HttpStatus.BAD_REQUEST;
            case ATTEMPTS_EXCEED, RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
        };

        return ResponseEntity.status(status)
                .body(response);
    }
}
