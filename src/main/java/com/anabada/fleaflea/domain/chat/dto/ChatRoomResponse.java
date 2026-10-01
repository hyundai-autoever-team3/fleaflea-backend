package com.anabada.fleaflea.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "채팅방 응답")
public record ChatRoomResponse(
        @Schema(description = "채팅방 ID", example = "1") Long id,
        @Schema(description = "상대 회원 ID", example = "2") Long friendId,
        @Schema(description = "상대 닉네임", example = "플리친구") String nickname,
        @Schema(description = "상대 프로필 이미지 URL. 이미지가 없으면 null") String profileImageUrl,
        @Schema(description = "현재 메시지 전송 가능 여부", example = "true") boolean canSend,
        @Schema(description = "마지막 메시지 ID. 메시지가 없으면 null", example = "42") Long lastMessageId,
        @Schema(description = "마지막 메시지 내용. 메시지가 없으면 null") String lastMessageContent,
        @Schema(description = "마지막 메시지 시각. 메시지가 없으면 null") LocalDateTime lastMessageAt,
        @Schema(description = "내가 마지막으로 읽은 메시지 ID", example = "40") long myLastReadId,
        @Schema(description = "상대가 마지막으로 읽은 메시지 ID", example = "38") long friendLastReadId,
        @Schema(description = "읽지 않은 메시지 수", example = "2") long unreadCount
) {
}
