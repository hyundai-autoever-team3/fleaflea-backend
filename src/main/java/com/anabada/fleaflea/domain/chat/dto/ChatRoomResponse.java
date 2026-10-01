package com.anabada.fleaflea.domain.chat.dto;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,
        Long friendId,
        String nickname,
        String profileImageUrl,
        boolean canSend,
        Long lastMessageId,
        String lastMessageContent,
        LocalDateTime lastMessageAt,
        long myLastReadId,
        long friendLastReadId,
        long unreadCount
) {
}
