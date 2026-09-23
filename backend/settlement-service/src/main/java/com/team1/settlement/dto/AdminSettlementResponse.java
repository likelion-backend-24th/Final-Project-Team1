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
        List<CategoryRanking> topCategories,
        /** Gemini 가 만든 한글 요약. 호출 안 했거나(summary=false) 실패/한도초과면 null - fail-open. */
        String aiSummary
) {
}
