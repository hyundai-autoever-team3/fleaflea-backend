package com.anabada.fleaflea.domain.chat.dto;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "채팅 메시지 응답")
public record ChatMessageResponse(
        @Schema(description = "메시지 ID", example = "42") Long id,
        @Schema(description = "채팅방 ID", example = "1") Long roomId,
        @Schema(description = "발신자 회원 ID", example = "2") Long senderId,
        @Schema(description = "메시지 내용", example = "안녕하세요") String content,
        @Schema(description = "중복 전송 방지용 클라이언트 메시지 UUID") String clientMessageId,
        @Schema(description = "메시지 생성 시각") LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getSenderId(),
                message.getContent(),
                message.getClientMessageId(),
                message.getCreatedAt()
        );
    }
}
