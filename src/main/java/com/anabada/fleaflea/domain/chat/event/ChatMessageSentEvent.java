package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;

public record ChatMessageSentEvent(
        Long memberId,
        Long friendId,
        ChatMessageResponse chatMessageResponse
) {

    public static ChatMessageSentEvent of(
            Long memberId,
            Long friendId,
            ChatMessageResponse chatMessageResponse
    ) {
        return new ChatMessageSentEvent(memberId, friendId, chatMessageResponse);
    }
}
