package com.anabada.fleaflea.domain.begrequest.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBeggingRequest(
        @NotBlank(message = "구걸 사유는 필수입니다.")
        String story
) {
}
