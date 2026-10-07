package com.anabada.fleaflea.domain.chat.dto;

import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatTypingResponse(
        @Schema(description = "채팅방 ID")
        Long roomId,

        @Schema(description = "입력 상태를 변경한 회원 ID")
        Long memberId,

        @Schema(description = "입력 중 여부")
        boolean typing
) {

    public static ChatTypingResponse from(
            ChatRoom chatRoom,
            Long memberId,
            boolean typing
    ) {
        return new ChatTypingResponse(chatRoom.getId(), memberId, typing);
    }
}
