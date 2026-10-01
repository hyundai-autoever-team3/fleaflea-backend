package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "채팅 메시지 전송 요청")
public record ChatMessageSendRequest(
        @Schema(description = "메시지 내용. 최대 2000자", example = "안녕하세요")
        @NotBlank
        @Size(max = 2000)
        String content,

        @Schema(description = "재시도 시 동일하게 유지할 클라이언트 메시지 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull
        UUID clientMessageId
) {
}
