package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank
        @Email
        @Schema(description = "PASSWORD_RESET 목적으로 인증한 일반 회원 이메일", example = "user@example.com")
        String email,

        @NotBlank
        @Schema(description = "PASSWORD_RESET 코드 검증 성공 응답의 일회용 토큰", example = "550e8400-e29b-41d4-a716-446655440000")
        String verificationToken,

        @NotBlank
        @Size(min = 8, max = 20)
        @Schema(description = "새 비밀번호(8~20자)", example = "NewPassword123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        String newPassword
) {
}
