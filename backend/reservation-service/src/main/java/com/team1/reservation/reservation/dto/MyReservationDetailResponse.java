package com.team1.reservation.reservation.dto;

import com.team1.reservation.reservation.entity.RefundState;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.round.entity.Round;

import java.time.Instant;

/**
 * 내 예약 상세(#82). 본인 예약이므로 연락처를 마스킹하지 않는다.
 *
 * <p>{@code ticketAvailable} 이 false 면 티켓이 아직 발급되지 않았거나 조회에 실패한 것이다.
 * 어느 쪽이든 예약 정보는 정상이므로 200 으로 내려간다.
 */
public record MyReservationDetailResponse(Long reservationId,
                                          String reservationNo,
                                          Long expoId,
                                          Long roundId,
                                          Instant startsAt,
                                          Instant endsAt,
                                          String contactName,
                                          String contactPhone,
                                          int headcount,
                                          int amount,
                                          ReservationStatus status,
                                          RefundState refundState,
                                          boolean ticketAvailable,
                                          ReservationTicketView ticket,
                                          Instant createdAt) {

    public static MyReservationDetailResponse of(Reservation reservation, Round round,
                                                 RefundState refundState, ReservationTicketView ticket) {
        return new MyReservationDetailResponse(
                reservation.getId(),
                reservation.getReservationNo(),
                reservation.getExpoId(),
                reservation.getRoundId(),
                round == null ? null : round.getStartsAt(),
                round == null ? null : round.getEndsAt(),
                reservation.getContactName(),
                reservation.getContactPhone(),
                reservation.getHeadcount(),
                reservation.getAmount(),
                reservation.getStatus(),
                refundState,
                ticket != null,
                ticket,
                reservation.getCreatedAt());
    }

    /** 연락처는 Log 에 남기지 않는다. */
    @Override
    public String toString() {
        return "MyReservationDetailResponse{reservationId=" + reservationId
                + ", reservationNo=" + reservationNo
                + ", roundId=" + roundId
                + ", status=" + status
                + ", refundState=" + refundState
                + ", ticketAvailable=" + ticketAvailable
                + '}';
    }
}
