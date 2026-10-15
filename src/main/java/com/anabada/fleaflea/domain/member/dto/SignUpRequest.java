package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest (
        @NotBlank(message = "이메일은 필수 값입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Schema(description = "SIGN_UP 목적으로 인증한 이메일", example = "user@example.com")
        String email,

        @NotBlank(message = "비밀번호는 필수 값입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
        @Schema(description = "비밀번호(8~20자)", example = "Password123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password,

        @NotBlank(message = "닉네임은 필수 값입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해주세요.")
        @Schema(description = "닉네임(2~20자)", example = "플리회원")
        String nickname,

        @NotBlank(message = "이메일 인증 토큰은 필수 값입니다")
        @Schema(description = "SIGN_UP 코드 검증 응답의 verificationToken을 이 필드에 전달", example = "550e8400-e29b-41d4-a716-446655440000")
        String verificationToken
) {
}
