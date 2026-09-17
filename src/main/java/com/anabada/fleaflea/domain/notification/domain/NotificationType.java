package com.anabada.fleaflea.domain.notification.domain;

public enum NotificationType {
    // 거래
    TRADE_REQUESTED,
    TRADE_ACCEPTED,
    TRADE_REJECTED,
    TRADE_CANCELLED,
    TRADE_COMPLETION_CONFIRMED,
    TRADE_COMPLETED,

    // 친구 추가
    FRIEND_REQUESTED,
    FRIEND_ACCEPTED
}