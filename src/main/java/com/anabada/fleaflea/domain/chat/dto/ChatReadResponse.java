package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅 메시지 읽음 처리 응답")
public record ChatReadResponse(
        @Schema(description = "채팅방 ID", example = "1") Long roomId,
        @Schema(description = "읽은 회원 ID", example = "2") Long memberId,
        @Schema(description = "마지막으로 읽은 메시지 ID", example = "42") long lastReadMessageId
) {
}
