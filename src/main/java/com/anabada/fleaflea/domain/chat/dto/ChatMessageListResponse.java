package com.anabada.fleaflea.domain.chat.dto;

import java.util.List;

public record ChatMessageListResponse(
        List<ChatMessageResponse> messages,
        Long nextCursor,
        boolean hasNext
) {
}
