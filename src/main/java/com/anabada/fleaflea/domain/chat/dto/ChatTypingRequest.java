package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "채팅 입력 상태 변경 요청")
public record ChatTypingRequest(
        @Schema(description = "입력 중 여부. 필수 입력값입니다.", example = "true")
        @NotNull
        Boolean typing
) {
}
