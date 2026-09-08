package com.team1.reservation.reservation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.service.ReservationCreation;

import java.time.Instant;

/**
 * @param paymentId 프론트가 PortOne 결제창을 열 때 쓰는 값. 무료 회차는 결제가 없으므로
 *                  이 필드가 응답에서 빠지고, {@code status} 가 바로 {@code CONFIRMED} 다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationResponse(Long reservationId,
                                  String reservationNo,
                                  Long roundId,
                                  int headcount,
                                  int amount,
                                  ReservationStatus status,
                                  Instant expiresAt,
                                  String paymentId) {

    public static ReservationResponse from(ReservationCreation creation) {
        Reservation reservation = creation.reservation();
        return new ReservationResponse(
                reservation.getId(),
                reservation.getReservationNo(),
                reservation.getRoundId(),
                reservation.getHeadcount(),
                reservation.getAmount(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                creation.paymentId());
    }
}
