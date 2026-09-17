package com.team1.recommendation.notification.dto;

import com.team1.recommendation.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationItem(
        Long id,
        Long expoId,
        String message,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationItem from(Notification n) {
        return new NotificationItem(n.getId(), n.getExpoId(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
