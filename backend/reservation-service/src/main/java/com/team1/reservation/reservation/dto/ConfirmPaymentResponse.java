package com.team1.reservation.reservation.dto;

import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;

import java.time.Instant;

public record ConfirmPaymentResponse(Long reservationId,
                                     ReservationStatus status,
                                     Instant confirmedAt) {

    public static ConfirmPaymentResponse from(Reservation reservation) {
        return new ConfirmPaymentResponse(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getConfirmedAt());
    }
}
