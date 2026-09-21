package com.team1.reservation.round.dto;

import java.time.Instant;

public record NearestDeadlineView(Long expoId, Instant nearestEndsAt) {}
