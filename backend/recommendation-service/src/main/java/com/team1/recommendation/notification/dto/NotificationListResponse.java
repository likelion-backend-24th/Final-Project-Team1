package com.team1.recommendation.notification.dto;

import java.util.List;

public record NotificationListResponse(List<NotificationItem> notifications, boolean hasNext) {}
