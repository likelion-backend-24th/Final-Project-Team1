package com.team1.settlement.dto;

public record AdminSettlementResponse(
        int year,
        int month,
        long totalRevenue,
        long totalRefund,
        long netRevenue,
        long platformFee,
        double feeRate
) {

}
