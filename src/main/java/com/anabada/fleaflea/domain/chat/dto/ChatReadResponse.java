package com.anabada.fleaflea.domain.chat.dto;

public record ChatReadResponse(
        Long roomId,
        Long memberId,
        long lastReadMessageId
) {
}
