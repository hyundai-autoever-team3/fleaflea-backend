package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 채팅방 목록 응답")
public record ChatRoomListResponse(
        @Schema(description = "현재 페이지의 채팅방")
        List<ChatRoomResponse> rooms,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext
) {

    public static ChatRoomListResponse from(List<ChatRoomResponse> rooms, boolean hasNext) {
        return new ChatRoomListResponse(rooms, hasNext);
    }
}
