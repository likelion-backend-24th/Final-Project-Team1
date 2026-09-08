package com.team1.reservation.reservation.dto;

import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;

import java.time.Instant;

public record ReservationResponse(Long reservationId,
                                  String reservationNo,
                                  Long roundId,
                                  int headcount,
                                  int amount,
                                  ReservationStatus status,
                                  Instant expiresAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getReservationNo(),
                reservation.getRoundId(),
                reservation.getHeadcount(),
                reservation.getAmount(),
                reservation.getStatus(),
                reservation.getExpiresAt());
    }
}
