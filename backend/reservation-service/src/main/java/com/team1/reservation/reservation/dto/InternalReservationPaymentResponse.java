package com.team1.reservation.reservation.dto;

import com.team1.payment.PaymentTransaction;

import java.time.Instant;

public record InternalReservationPaymentResponse(
        String paymentId,
        int amount,
        String status,
        Instant paidAt,
        Instant cancelledAt,
        Instant updatedAt,
        Long reservationId
) {
    public static  InternalReservationPaymentResponse of(PaymentTransaction tx){
        return new InternalReservationPaymentResponse(
                tx.getPaymentId(),
                tx.getAmount(),
                tx.getStatus().name(),
                tx.getPaidAt(),
                tx.getCancelledAt(),
                tx.getUpdatedAt(),
                tx.getRefId()
        );
    }
}
