package com.team1.settlement.dto;

public record AdminSettlementResponse(
        int year,
        Integer month,
        long totalRevenue,
        long totalRefund,
        long netRevenue,
        long platformFee,
        double feeRate
) {

}
