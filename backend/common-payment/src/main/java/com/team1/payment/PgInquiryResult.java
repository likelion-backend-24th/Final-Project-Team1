package com.team1.payment;

public record PgInquiryResult(
        PgPaymentStatus status,
        Integer amount,
        String pgTransactionId,
        String responseCode,
        String failureReason,
        String storeId,
        String channelKey
) {
}
