package com.team1.recommendation.internal.dto;

import com.team1.recommendation.activity.entity.EventType;

public record BehaviorEventRequest(Long userId, Long expoId, EventType eventType) {}
