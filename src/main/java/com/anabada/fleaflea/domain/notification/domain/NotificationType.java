package com.anabada.fleaflea.domain.notification.domain;

public enum NotificationType {
    // 거래
    TRADE_REQUESTED,
    TRADE_ACCEPTED,
    TRADE_REJECTED,
    TRADE_CANCELLED,
    TRADE_COMPLETED,

    // 친구
    FRIEND_REQUESTED,
    FRIEND_ACCEPTED
}