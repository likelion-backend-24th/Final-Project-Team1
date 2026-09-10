package com.team1.reservation.reservation.dto;

import com.team1.reservation.reservation.entity.RefundState;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.round.entity.Round;

import java.time.Instant;

/** 내 예약 목록 한 줄(#82). 목록에는 연락처와 QR 을 싣지 않는다. */
public record MyReservationResponse(Long reservationId,
                                    String reservationNo,
                                    Long expoId,
                                    Long roundId,
                                    Instant startsAt,
                                    Instant endsAt,
                                    int headcount,
                                    int amount,
                                    ReservationStatus status,
                                    RefundState refundState,
                                    Instant createdAt) {

    /** round 는 없을 수 없지만, 방어적으로 null 이면 시각을 비워 보낸다. */
    public static MyReservationResponse of(Reservation reservation, Round round, RefundState refundState) {
        return new MyReservationResponse(
                reservation.getId(),
                reservation.getReservationNo(),
                reservation.getExpoId(),
                reservation.getRoundId(),
                round == null ? null : round.getStartsAt(),
                round == null ? null : round.getEndsAt(),
                reservation.getHeadcount(),
                reservation.getAmount(),
                reservation.getStatus(),
                refundState,
                reservation.getCreatedAt());
    }
}
