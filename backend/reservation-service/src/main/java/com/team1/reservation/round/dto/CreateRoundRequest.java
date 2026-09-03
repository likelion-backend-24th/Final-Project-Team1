package com.team1.reservation.round.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;


public record CreateRoundRequest(

        @NotNull Instant startsAt,

        @NotNull Instant endsAt,

        @NotNull @Min(1) Integer capacity,

        @Min(0) Integer fee) {


    public int feeOrZero() {
        return fee == null ? 0 : fee;
    }
}
