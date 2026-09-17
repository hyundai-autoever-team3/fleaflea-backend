package com.anabada.fleaflea.domain.notification.dto;

import com.anabada.fleaflea.domain.notification.domain.Notification;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "알림 응답")
public record NotificationResponse(
        @Schema(description = "알림 ID", example = "10")
        Long notificationId,

        @Schema(description = "알림 발생 유형", example = "TRADE_REQUESTED")
        NotificationType type,

        @Schema(description = "알림이 참조하는 도메인 유형", example = "COLLECTION_TRADE_REQUEST")
        NotificationReferenceType referenceType,

        @Schema(description = "참조 대상 ID. referenceType에 해당하는 도메인의 식별자", example = "7")
        Long referenceId,

        @Schema(description = "알림 메시지", example = "민지님이 레트로 카메라 교환을 요청했습니다.")
        String message,

        @Schema(description = "알림 읽음 여부", example = "false")
        boolean isRead,

        @Schema(description = "알림 생성 일시", example = "2026-09-16T23:20:00")
        LocalDateTime createdAt

) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getType(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}