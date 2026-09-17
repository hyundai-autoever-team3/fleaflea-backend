package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.notification.domain.Notification;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotificationFixture {
    public static Notification createNotification(Member receiver) {
        return Notification.create(
                receiver,
                NotificationType.TRADE_REQUESTED,
                NotificationReferenceType.ITEM_TRADE_REQUEST,
                10L,
                "거래 요청이 도착했습니다."
        );
    }
}