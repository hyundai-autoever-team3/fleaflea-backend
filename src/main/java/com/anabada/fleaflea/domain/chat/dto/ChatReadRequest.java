package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "채팅 메시지 읽음 처리 요청")
public record ChatReadRequest(
        @Schema(description = "실제로 확인한 메시지 ID. 필수 입력값입니다.", example = "42")
        @NotNull
        Long messageId
) {
}
