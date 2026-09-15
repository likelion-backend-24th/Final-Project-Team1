package com.team1.settlement.client;

import java.time.Instant;

public record ReservationPaymentItem(
        String paymentId,
        int amount,
        String status,
        Instant paidAt,
        Instant cancelledAt,
        Instant updatedAt,
        Long reservationId
) {


}
