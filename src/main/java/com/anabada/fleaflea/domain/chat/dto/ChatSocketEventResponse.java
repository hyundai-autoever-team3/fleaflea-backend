package com.anabada.fleaflea.domain.chat.dto;

import com.anabada.fleaflea.domain.chat.event.ChatEvent;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatSocketEventResponse(
        @Schema(description = "채팅 이벤트 종류: chat-message, chat-read 또는 chat-typing")
        String type,

        @Schema(description = "메시지, 읽음 상태 또는 입력 상태")
        Object payload
) {

    public static ChatSocketEventResponse from(ChatEvent event) {
        return new ChatSocketEventResponse(event.eventName(), event.payload());
    }
}
