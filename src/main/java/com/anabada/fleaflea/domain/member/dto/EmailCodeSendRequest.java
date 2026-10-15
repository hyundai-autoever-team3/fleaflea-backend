package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.anabada.fleaflea.domain.member.domain.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmailCodeSendRequest(
        @NotBlank @Email
        @Schema(description = "인증 코드를 수신할 이메일", example = "user@example.com")
        String email,

        @NotNull
        @Schema(description = "회원가입: SIGN_UP, 비밀번호 재설정: PASSWORD_RESET", example = "SIGN_UP")
        EmailVerificationPurpose purpose
) {
}
