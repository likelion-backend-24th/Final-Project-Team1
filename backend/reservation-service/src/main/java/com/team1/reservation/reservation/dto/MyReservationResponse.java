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
                                    String expoTitle,
                                    Long roundId,
                                    Integer roundSequence,
                                    Instant startsAt,
                                    Instant endsAt,
                                    int headcount,
                                    int amount,
                                    ReservationStatus status,
                                    RefundState refundState,
                                    Instant createdAt) {

    /**
     * round 는 없을 수 없지만, 방어적으로 null 이면 시각을 비워 보낸다.
     * expoTitle·roundSequence 는 표시용이라 못 채워도 null 로 두고 화면이 날짜로 물러난다.
     */
    public static MyReservationResponse of(Reservation reservation, Round round,
                                           String expoTitle, Integer roundSequence,
                                           RefundState refundState) {
        return new MyReservationResponse(
                reservation.getId(),
                reservation.getReservationNo(),
                reservation.getExpoId(),
                expoTitle,
                reservation.getRoundId(),
                roundSequence,
                round == null ? null : round.getStartsAt(),
                round == null ? null : round.getEndsAt(),
                reservation.getHeadcount(),
                reservation.getAmount(),
                reservation.getStatus(),
                refundState,
                reservation.getCreatedAt());
    }
}
