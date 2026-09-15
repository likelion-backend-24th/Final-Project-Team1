package com.team1.reservation.reservation.dto;

import com.team1.reservation.reservation.entity.TicketDispatch;

import java.time.Instant;


// 자동 회수를 포기한 통지(S7-6). REVOKE 건은 취소된 예약의 티켓이 아직 살아 있다는 뜻이다.
public record GaveUpDispatchResponse(Long dispatchId,
                                     Long reservationId,
                                     String reservationNo,
                                     String type,
                                     int attempts,
                                     String lastError,
                                     Instant updatedAt) {

    public static GaveUpDispatchResponse from(TicketDispatch dispatch) {
        return new GaveUpDispatchResponse(
                dispatch.getId(),
                dispatch.getReservationId(),
                dispatch.getReservationNo(),
                dispatch.getType().name(),
                dispatch.getAttempts(),
                dispatch.getLastError(),
                dispatch.getUpdatedAt());
    }
}
