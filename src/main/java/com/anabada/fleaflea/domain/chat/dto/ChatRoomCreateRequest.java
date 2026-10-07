package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "친구 채팅방 생성 요청")
public record ChatRoomCreateRequest(
        @Schema(description = "대화할 친구의 회원 ID. 필수 입력값입니다.", example = "2")
        @NotNull
        Long friendId
) {
}
