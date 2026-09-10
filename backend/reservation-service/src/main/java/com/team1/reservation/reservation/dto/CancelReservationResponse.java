package com.team1.reservation.reservation.dto;

import com.team1.reservation.reservation.entity.RefundState;

import java.time.Instant;

/** 취소 결과. 환불 상태를 함께 내려줘야 화면이 상태 조합표를 알지 않아도 된다. */
public record CancelReservationResponse(Long reservationId,
                                        String status,
                                        RefundState refundState,
                                        Instant cancelledAt) {
}
