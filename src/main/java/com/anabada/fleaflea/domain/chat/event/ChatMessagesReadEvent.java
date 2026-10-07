package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;

public record ChatMessagesReadEvent(
        Long memberId,
        Long friendId,
        ChatReadResponse chatReadResponse
) {

    public static ChatMessagesReadEvent of(
            Long memberId,
            Long friendId,
            ChatReadResponse chatReadResponse
    ) {
        return new ChatMessagesReadEvent(memberId, friendId, chatReadResponse);
    }
}
