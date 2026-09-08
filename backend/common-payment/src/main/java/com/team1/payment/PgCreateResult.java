package com.team1.payment;

public record PgCreateResult(
        boolean success,
        String responseCode
) {
}
