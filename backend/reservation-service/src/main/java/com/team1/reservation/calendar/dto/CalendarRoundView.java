package com.team1.reservation.calendar.dto;

import com.team1.reservation.round.dto.InternalRoundResponse;

import java.time.Instant;

public record CalendarRoundView(Long roundId, Long expoId,
                                String expoTitle, int sequence,
                                Instant startsAt, Instant endsAt) {

    public static CalendarRoundView of(InternalRoundResponse round, String expoTitle){
        return new CalendarRoundView(round.roundId(), round.expoId(), expoTitle,
                round.sequence(), round.startsAt(),round.endsAt());
    }

}
