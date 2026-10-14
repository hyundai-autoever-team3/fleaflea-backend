package com.anabada.fleaflea.domain.member.dto;

import com.anabada.fleaflea.domain.member.domain.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EmailCodeVerifyRequest(
        @NotBlank @Email
        String email,

        @NotNull
        EmailVerificationPurpose purpose,

        @NotBlank
        @Pattern(regexp = "[0-9]{6}")
        String code
) {
}
