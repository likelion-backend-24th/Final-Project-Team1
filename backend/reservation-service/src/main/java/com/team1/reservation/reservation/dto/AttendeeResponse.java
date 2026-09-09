package com.team1.reservation.reservation.dto;

import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;

import java.time.Instant;

//예약자 명단 한 줄. 박람회-Service 의 엑셀 다운로드가 쓴다.

public record AttendeeResponse(Long reservationId,
                               String reservationNo,
                               Long roundId,
                               String contactName,
                               String contactPhone,
                               int headcount,
                               int amount,
                               ReservationStatus status,
                               Instant createdAt) {

    public static AttendeeResponse from(Reservation reservation) {
        return new AttendeeResponse(
                reservation.getId(),
                reservation.getReservationNo(),
                reservation.getRoundId(),
                reservation.getContactName(),
                reservation.getContactPhone(),
                reservation.getHeadcount(),
                reservation.getAmount(),
                reservation.getStatus(),
                reservation.getCreatedAt());
    }

    /**
     * 이름·연락처를 제외한다. 이 DTO 는 명단 전체를 담고 오가므로, 오류 Log 한 줄에
     * 수백 명의 개인정보가 통째로 남을 수 있다.
     */
    @Override
    public String toString() {
        return "AttendeeResponse{reservationId=" + reservationId
                + ", reservationNo=" + reservationNo
                + ", roundId=" + roundId
                + ", headcount=" + headcount
                + ", status=" + status
                + '}';
    }
}
