package com.anabada.fleaflea.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuth2SignupRequest(
        @NotBlank
        @Size(max = 50)
        String nickname
) {
}
