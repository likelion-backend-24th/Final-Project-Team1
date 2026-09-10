package com.team1.reservation.reservation.dto;

import java.time.Instant;

/** 취소 결과. 환불 여부를 함께 돌려줘야 사용자가 돈이 돌아오는지 알 수 있다. */
public record CancelReservationResponse(Long reservationId,
                                        String status,
                                        Instant cancelledAt,
                                        boolean refunded,
                                        int refundAmount) {
}
