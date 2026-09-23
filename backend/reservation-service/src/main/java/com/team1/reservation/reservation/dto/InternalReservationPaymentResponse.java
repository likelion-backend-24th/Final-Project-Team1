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
        Long reservationId,
        Long expoId
) {
    /** expoId 는 예약 엔티티에만 있어 결제 트랜잭션만으로는 못 채운다 - 호출부가 따로 붙인다. */
    public static InternalReservationPaymentResponse of(PaymentTransaction tx, Long expoId){
        return new InternalReservationPaymentResponse(
                tx.getPaymentId(),
                tx.getAmount(),
                tx.getStatus().name(),
                tx.getPaidAt(),
                tx.getCancelledAt(),
                tx.getUpdatedAt(),
                tx.getRefId(),
                expoId
        );
    }
}
