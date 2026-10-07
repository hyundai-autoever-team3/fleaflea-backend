package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatTypingResponse;

public record ChatTypingChangedEvent(
        Long memberId,
        Long friendId,
        ChatTypingResponse chatTypingResponse
) {

    public static ChatTypingChangedEvent of(
            Long memberId,
            Long friendId,
            ChatTypingResponse chatTypingResponse
    ) {
        return new ChatTypingChangedEvent(memberId, friendId, chatTypingResponse);
    }
}
