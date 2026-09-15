package com.team1.settlement.client;

import java.time.Instant;

public record ExpoPromotionPaymentItem(
        String paymentId,
        int amount,
        Instant paidAt,
        String status,
        Long expoId
) {
}
