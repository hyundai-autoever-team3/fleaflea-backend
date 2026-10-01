package com.anabada.fleaflea.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatMessageSendRequest(
        @NotBlank
        @Size(max = 2000)
        String content,

        @NotNull
        UUID clientMessageId
) {
}
