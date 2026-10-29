package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;

public record ChatMessageSentEvent(
        Long memberId,
        Long friendId,
        String senderNickname,
        ChatMessageResponse chatMessageResponse
) {

    public static ChatMessageSentEvent of(
            Long memberId,
            Long friendId,
            String senderNickname,
            ChatMessageResponse chatMessageResponse
    ) {
        return new ChatMessageSentEvent(memberId, friendId, senderNickname, chatMessageResponse);
    }
}
