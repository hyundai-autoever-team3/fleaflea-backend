package com.anabada.fleaflea.domain.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatReadRequest(
        @NotNull
        @Positive
        Long messageId
) {
}
