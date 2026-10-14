package com.anabada.fleaflea.domain.member.dto;

import com.anabada.fleaflea.domain.member.domain.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmailCodeSendRequest(
        @NotBlank @Email
        String email,

        @NotNull
        EmailVerificationPurpose purpose
) {
}
