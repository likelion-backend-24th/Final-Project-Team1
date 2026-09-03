package com.team1.reservation.round.dto;

import com.team1.reservation.round.entity.Round;

import java.time.Instant;

public record RoundResponse(Long roundId,
                            Instant startsAt,
                            Instant endsAt,
                            int capacity,
                            int remaining,
                            int fee) {

    public static RoundResponse from(Round round) {
        return new RoundResponse(
                round.getId(),
                round.getStartsAt(),
                round.getEndsAt(),
                round.getCapacity(),
                round.remaining(),
                round.getFee());
    }
}
