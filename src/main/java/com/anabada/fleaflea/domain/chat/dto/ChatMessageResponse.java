package com.anabada.fleaflea.domain.chat.dto;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long senderId,
        String content,
        String clientMessageId,
        LocalDateTime createdAt
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
