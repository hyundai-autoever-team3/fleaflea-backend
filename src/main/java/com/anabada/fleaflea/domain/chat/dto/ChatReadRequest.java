package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "채팅 메시지 읽음 처리 요청")
public record ChatReadRequest(
        @Schema(description = "실제로 확인한 메시지 ID", example = "42")
        @NotNull(message = "메시지 ID는 필수입니다.")
        @Positive(message = "메시지 ID는 양수여야 합니다.")
        Long messageId
) {
}
