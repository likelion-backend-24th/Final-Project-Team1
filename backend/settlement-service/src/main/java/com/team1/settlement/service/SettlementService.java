package com.team1.settlement.service;

import com.team1.settlement.client.ExpoPromotionPaymentClient;
import com.team1.settlement.client.ExpoPromotionPaymentItem;
import com.team1.settlement.client.ReservationPaymentClient;
import com.team1.settlement.client.ReservationPaymentItem;
import com.team1.settlement.dto.AdminSettlementResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class SettlementService {

    private static final double FEE_RATE = 0.10;

    private final ReservationPaymentClient reservationPaymentClient;
    private final ExpoPromotionPaymentClient expoPromotionPaymentClient;

    public SettlementService(ReservationPaymentClient reservationPaymentClient,
                             ExpoPromotionPaymentClient expoPromotionPaymentClient) {
        this.reservationPaymentClient = reservationPaymentClient;
        this.expoPromotionPaymentClient = expoPromotionPaymentClient;
    }

    public AdminSettlementResponse getSettlement(int year, Integer month) {
        Instant from;
        Instant to;

        if (month != null) {
            YearMonth target = YearMonth.of(year, month);
            from = target.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            to = target.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        } else {
            from = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
            to = LocalDate.of(year, 12, 31).atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        }

        List<ReservationPaymentItem> reservationPayments = reservationPaymentClient.getPayments(from, to);
        List<ExpoPromotionPaymentItem> promotionPayments = expoPromotionPaymentClient.getPayments(from, to);

        long totalRevenue = sumReservation(reservationPayments, "PAID") + sumPromotion(promotionPayments, "PAID");
        long totalRefund = sumReservation(reservationPayments, "CANCELLED") + sumPromotion(promotionPayments, "CANCELLED");
        long netRevenue = totalRevenue - totalRefund;
        long platformFee = Math.round(netRevenue * FEE_RATE);

        return new AdminSettlementResponse(year, month, totalRevenue, totalRefund, netRevenue, platformFee, FEE_RATE);
    }

    private long sumReservation(List<ReservationPaymentItem> items, String status) {
        return items.stream()
                .filter(item -> item.status().equals(status))
                .mapToLong(ReservationPaymentItem::amount)
                .sum();
    }

    private long sumPromotion(List<ExpoPromotionPaymentItem> items, String status) {
        return items.stream()
                .filter(item -> item.status().equals(status))
                .mapToLong(ExpoPromotionPaymentItem::amount)
                .sum();
    }
}
