package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "친구 채팅방 생성 요청")
public record ChatRoomCreateRequest(
        @Schema(description = "대화할 친구의 회원 ID", example = "2")
        @NotNull
        @Positive
        Long friendId
) {
}
