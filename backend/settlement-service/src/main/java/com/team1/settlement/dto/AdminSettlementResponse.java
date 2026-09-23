package com.team1.settlement.dto;

import java.util.List;

public record AdminSettlementResponse(
        SettlementPeriod period,
        String from,
        String to,
        long totalRevenue,
        long totalRefund,
        long netRevenue,
        long platformFee,
        double feeRate,
        long reservationRevenue,
        long reservationRefund,
        long promotionRevenue,
        long promotionRefund,
        int reservationPaidCount,
        int reservationRefundCount,
        int promotionPaidCount,
        int promotionRefundCount,
        List<SettlementBucket> buckets,
        List<ExpoRanking> topExpos,
        List<CategoryRanking> topCategories
) {
}
