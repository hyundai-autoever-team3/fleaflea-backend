package com.anabada.fleaflea.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "미확인 알림 개수 응답")
public record NotificationUnreadCountResponse(

        @Schema(description = "미확인 알림 개수", example = "3")
        long unreadCount
) {
}