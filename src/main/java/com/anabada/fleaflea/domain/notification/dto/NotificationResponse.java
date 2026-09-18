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

        @Schema(
                description = """
                        알림 발생 유형
                        - TRADE_REQUESTED: 거래 요청
                        - TRADE_ACCEPTED: 거래 요청 수락
                        - TRADE_REJECTED: 거래 요청 거절
                        - TRADE_CANCELLED: 거래 요청 취소
                        - TRADE_COMPLETED: 거래 완료
                        - FRIEND_REQUESTED: 친구 요청
                        - FRIEND_ACCEPTED: 친구 요청 수락
                        - POKE_RECEIVED: 콕찌르기 수신
                        """,
                example = "POKE_RECEIVED"
        )
        NotificationType type,

        @Schema(
                description = """
                        알림이 참조하는 도메인 유형
                        - ITEM_TRADE_REQUEST: 일반 상품 거래 요청
                        - COLLECTION_TRADE_REQUEST: 도감 물건 거래 요청
                        - BEG_REQUEST: 구걸 요청
                        - FRIENDSHIP: 친구 관계
                        - MEMBER_POKE: 콕찌르기
                        """,
                example = "MEMBER_POKE"
        )
        NotificationReferenceType referenceType,

        @Schema(
                description = """
                        참조 대상 ID
                        
                        referenceType에 따라 의미가 달라짐
                        - ITEM_TRADE_REQUEST: tradeRequestId
                        - COLLECTION_TRADE_REQUEST: collectionTradeRequestId
                        - BEG_REQUEST: begRequestId
                        - FRIENDSHIP: friendshipId
                        - MEMBER_POKE: pokeId
                        """,
                example = "7"
        )
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