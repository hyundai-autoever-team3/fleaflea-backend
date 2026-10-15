package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.anabada.fleaflea.domain.member.domain.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EmailCodeVerifyRequest(
        @NotBlank @Email
        @Schema(description = "코드 발송 시 사용한 이메일", example = "user@example.com")
        String email,

        @NotNull
        @Schema(description = "코드 발송 시 사용한 인증 목적", example = "SIGN_UP")
        EmailVerificationPurpose purpose,

        @NotBlank
        @Pattern(regexp = "[0-9]{6}")
        @Schema(description = "메일로 받은 6자리 숫자 코드(앞자리 0 포함)", example = "012345")
        String code
) {
}
