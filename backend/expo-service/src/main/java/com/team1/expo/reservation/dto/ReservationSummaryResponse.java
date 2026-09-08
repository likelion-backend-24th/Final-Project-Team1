package com.team1.expo.reservation.dto;

import java.util.List;

public record ReservationSummaryResponse(
        Long expoId,
        List<RoundSummary> rounds
) {
    public record RoundSummary(
            Long roundId,
            int capacity,
            int confirmed,
            int cancelled,
            Integer checkedIn
    ) {}
}
