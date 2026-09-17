package com.team1.recommendation.notification.entity;

public enum NotificationType {
    /** 취향 기반 신규 박람회 추천(#193). 같은 박람회는 한 번만 보낸다. */
    RECOMMENDATION,
    /** 예약 확정(#231). 예약마다 한 번 보낸다. */
    RESERVATION_CONFIRMED
}
