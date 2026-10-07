package com.anabada.fleaflea.domain.chat.dto;

import com.anabada.fleaflea.domain.chat.event.ChatEventType;
import com.anabada.fleaflea.domain.chat.event.ChatMessageSentEvent;
import com.anabada.fleaflea.domain.chat.event.ChatMessagesReadEvent;
import com.anabada.fleaflea.domain.chat.event.ChatTypingChangedEvent;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatSocketEventResponse<T>(
        @Schema(description = "채팅 이벤트 종류: chat-message, chat-read 또는 chat-typing")
        ChatEventType type,

        @Schema(description = "이벤트 종류에 해당하는 메시지, 읽음 상태 또는 입력 상태")
        T payload
) {

    public static ChatSocketEventResponse<ChatMessageResponse> from(ChatMessageSentEvent chatMessageSentEvent) {
        return new ChatSocketEventResponse<>(
                ChatEventType.MESSAGE_SENT,
                chatMessageSentEvent.chatMessageResponse()
        );
    }

    public static ChatSocketEventResponse<ChatReadResponse> from(ChatMessagesReadEvent chatMessagesReadEvent) {
        return new ChatSocketEventResponse<>(
                ChatEventType.MESSAGES_READ,
                chatMessagesReadEvent.chatReadResponse()
        );
    }

    public static ChatSocketEventResponse<ChatTypingResponse> from(ChatTypingChangedEvent chatTypingChangedEvent) {
        return new ChatSocketEventResponse<>(
                ChatEventType.TYPING_CHANGED,
                chatTypingChangedEvent.chatTypingResponse()
        );
    }
}
