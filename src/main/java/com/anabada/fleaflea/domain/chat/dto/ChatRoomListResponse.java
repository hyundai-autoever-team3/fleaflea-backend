package com.anabada.fleaflea.domain.chat.dto;

import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomResponse> rooms,
        boolean hasNext
) {
}
