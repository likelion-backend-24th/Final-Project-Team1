package com.team1.recommendation.notification.dto;

import com.team1.recommendation.notification.entity.Notification;
import com.team1.recommendation.notification.entity.NotificationType;

import java.time.Instant;

public record NotificationItem(
        Long id,
        Long expoId,
        NotificationType type,
        String message,
        boolean isRead,
        Instant createdAt
) {
    public static NotificationItem from(Notification n) {
        return new NotificationItem(n.getId(), n.getExpoId(), n.getType(), n.getMessage(),
                n.isRead(), n.getCreatedAt());
    }
}
