package com.team1.payment;

public record PgCancelResult(
        boolean success,
        String responseCode
) {
}
