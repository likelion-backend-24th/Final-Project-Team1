package com.team1.settlement.service;

import com.team1.settlement.client.ExpoDirectoryClient;
import com.team1.settlement.client.ExpoPromotionPaymentClient;
import com.team1.settlement.client.ExpoPromotionPaymentItem;
import com.team1.settlement.client.ReservationPaymentClient;
import com.team1.settlement.client.ReservationPaymentItem;
import com.team1.settlement.dto.AdminSettlementResponse;
import com.team1.settlement.dto.CategoryRanking;
import com.team1.settlement.dto.ExpoRanking;
import com.team1.settlement.dto.SettlementBucket;
import com.team1.settlement.dto.SettlementPeriod;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SettlementService {

    private static final double FEE_RATE = 0.10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int TOP_EXPO_LIMIT = 5;
    private static final String UNKNOWN_CATEGORY = "기타";

    private final ReservationPaymentClient reservationPaymentClient;
    private final ExpoPromotionPaymentClient expoPromotionPaymentClient;
    private final ExpoDirectoryClient expoDirectoryClient;

    public SettlementService(ReservationPaymentClient reservationPaymentClient,
                             ExpoPromotionPaymentClient expoPromotionPaymentClient,
                             ExpoDirectoryClient expoDirectoryClient) {
        this.reservationPaymentClient = reservationPaymentClient;
        this.expoPromotionPaymentClient = expoPromotionPaymentClient;
        this.expoDirectoryClient = expoDirectoryClient;
    }

    public AdminSettlementResponse getSettlement(SettlementPeriod period, LocalDate date) {
        LocalDate start = rangeStart(period, date);
        LocalDate end = rangeEnd(period, start);

        Instant from = start.atStartOfDay(KST).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(KST).toInstant();

        List<ReservationPaymentItem> reservationPayments = reservationPaymentClient.getPayments(from, to);
        List<ExpoPromotionPaymentItem> promotionPayments = expoPromotionPaymentClient.getPayments(from, to);

        long reservationRevenue = sum(reservationPayments, ReservationPaymentItem::status, "PAID", ReservationPaymentItem::amount);
        long reservationRefund = sum(reservationPayments, ReservationPaymentItem::status, "CANCELLED", ReservationPaymentItem::amount);
        long promotionRevenue = sum(promotionPayments, ExpoPromotionPaymentItem::status, "PAID", ExpoPromotionPaymentItem::amount);
        long promotionRefund = sum(promotionPayments, ExpoPromotionPaymentItem::status, "CANCELLED", ExpoPromotionPaymentItem::amount);

        long totalRevenue = reservationRevenue + promotionRevenue;
        long totalRefund = reservationRefund + promotionRefund;
        long netRevenue = totalRevenue - totalRefund;
        long platformFee = Math.round(netRevenue * FEE_RATE);

        int reservationPaidCount = count(reservationPayments, ReservationPaymentItem::status, "PAID");
        int reservationRefundCount = count(reservationPayments, ReservationPaymentItem::status, "CANCELLED");
        int promotionPaidCount = count(promotionPayments, ExpoPromotionPaymentItem::status, "PAID");
        int promotionRefundCount = count(promotionPayments, ExpoPromotionPaymentItem::status, "CANCELLED");

        List<SettlementBucket> buckets = buildBuckets(period, start, end, reservationPayments, promotionPayments);
        List<ExpoRanking> topExpos = buildTopExpos(reservationPayments, promotionPayments);
        List<CategoryRanking> topCategories = buildTopCategories(reservationPayments, promotionPayments);

        return new AdminSettlementResponse(
                period, start.toString(), end.toString(),
                totalRevenue, totalRefund, netRevenue, platformFee, FEE_RATE,
                reservationRevenue, reservationRefund, promotionRevenue, promotionRefund,
                reservationPaidCount, reservationRefundCount, promotionPaidCount, promotionRefundCount,
                buckets, topExpos, topCategories);
    }

    private LocalDate rangeStart(SettlementPeriod period, LocalDate date) {
        return switch (period) {
            case DAY -> date;
            case WEEK -> date.with(DayOfWeek.MONDAY);
            case MONTH -> YearMonth.from(date).atDay(1);
            case YEAR -> LocalDate.of(date.getYear(), 1, 1);
        };
    }

    private LocalDate rangeEnd(SettlementPeriod period, LocalDate start) {
        return switch (period) {
            case DAY -> start;
            case WEEK -> start.plusDays(6);
            case MONTH -> YearMonth.from(start).atEndOfMonth();
            case YEAR -> LocalDate.of(start.getYear(), 12, 31);
        };
    }

    /**
     * DAY·WEEK·MONTH 는 하루 단위로, YEAR 는 달 단위로 쪼갠다 - 1년을 365칸 달력으로 그리는 건
     * 의미가 없고, 화면의 달력·클릭 상세는 MONTH 뷰에서만 쓴다.
     *
     * <p>결제는 paidAt, 환불은 cancelledAt(프로모션은 그 필드가 없어 paidAt으로 근사) 기준으로 칸을
     * 정한다 - "언제 발생했는지" 기준이라야 그 날짜를 눌렀을 때 보이는 숫자와 맞는다.
     */
    private List<SettlementBucket> buildBuckets(SettlementPeriod period, LocalDate start, LocalDate end,
                                                List<ReservationPaymentItem> reservationPayments,
                                                List<ExpoPromotionPaymentItem> promotionPayments) {
        boolean byMonth = period == SettlementPeriod.YEAR;
        Map<String, long[]> byLabel = new LinkedHashMap<>(); // [revenue, refund]

        if (byMonth) {
            for (int m = 1; m <= 12; m++) {
                byLabel.put(YearMonth.of(start.getYear(), m).format(MONTH_LABEL), new long[2]);
            }
        } else {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                byLabel.put(d.format(DAY_LABEL), new long[2]);
            }
        }

        for (ReservationPaymentItem item : reservationPayments) {
            if ("PAID".equals(item.status()) && item.paidAt() != null) {
                addTo(byLabel, label(item.paidAt(), byMonth), 0, item.amount());
            } else if ("CANCELLED".equals(item.status())) {
                Instant at = item.cancelledAt() != null ? item.cancelledAt() : item.updatedAt();
                if (at != null) addTo(byLabel, label(at, byMonth), 1, item.amount());
            }
        }
        for (ExpoPromotionPaymentItem item : promotionPayments) {
            if (item.paidAt() == null) continue;
            String label = label(item.paidAt(), byMonth);
            if ("PAID".equals(item.status())) addTo(byLabel, label, 0, item.amount());
            else if ("CANCELLED".equals(item.status())) addTo(byLabel, label, 1, item.amount());
        }

        List<SettlementBucket> buckets = new ArrayList<>();
        byLabel.forEach((label, rev) -> buckets.add(new SettlementBucket(label, rev[0], rev[1], rev[0] - rev[1])));
        return buckets;
    }

    private String label(Instant at, boolean byMonth) {
        return byMonth ? MONTH_LABEL.format(at.atZone(KST)) : DAY_LABEL.format(at.atZone(KST));
    }

    private void addTo(Map<String, long[]> byLabel, String label, int index, long amount) {
        long[] slot = byLabel.get(label);
        if (slot != null) slot[index] += amount;
    }

    /** 랭킹은 환불을 빼지 않은 결제(PAID) 총액 기준이다 - "매출" 이라는 이름 그대로. */
    private List<ExpoRanking> buildTopExpos(List<ReservationPaymentItem> reservationPayments,
                                            List<ExpoPromotionPaymentItem> promotionPayments) {
        Map<Long, Long> revenueByExpo = new LinkedHashMap<>();
        for (ReservationPaymentItem item : reservationPayments) {
            if ("PAID".equals(item.status()) && item.expoId() != null) {
                revenueByExpo.merge(item.expoId(), (long) item.amount(), Long::sum);
            }
        }
        for (ExpoPromotionPaymentItem item : promotionPayments) {
            if ("PAID".equals(item.status()) && item.expoId() != null) {
                revenueByExpo.merge(item.expoId(), (long) item.amount(), Long::sum);
            }
        }
        if (revenueByExpo.isEmpty()) return List.of();

        List<Map.Entry<Long, Long>> sorted = revenueByExpo.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(TOP_EXPO_LIMIT)
                .toList();
        Map<Long, String> titles = expoDirectoryClient.titles(sorted.stream().map(Map.Entry::getKey).toList());

        return sorted.stream()
                .map(e -> new ExpoRanking(e.getKey(), titles.getOrDefault(e.getKey(), ""), e.getValue()))
                .toList();
    }

    private List<CategoryRanking> buildTopCategories(List<ReservationPaymentItem> reservationPayments,
                                                      List<ExpoPromotionPaymentItem> promotionPayments) {
        Map<Long, Long> revenueByExpo = new LinkedHashMap<>();
        for (ReservationPaymentItem item : reservationPayments) {
            if ("PAID".equals(item.status()) && item.expoId() != null) {
                revenueByExpo.merge(item.expoId(), (long) item.amount(), Long::sum);
            }
        }
        for (ExpoPromotionPaymentItem item : promotionPayments) {
            if ("PAID".equals(item.status()) && item.expoId() != null) {
                revenueByExpo.merge(item.expoId(), (long) item.amount(), Long::sum);
            }
        }
        if (revenueByExpo.isEmpty()) return List.of();

        Map<Long, String> categories = expoDirectoryClient.categories(revenueByExpo.keySet());
        Map<String, Long> revenueByCategory = new LinkedHashMap<>();
        revenueByExpo.forEach((expoId, revenue) ->
                revenueByCategory.merge(categories.getOrDefault(expoId, UNKNOWN_CATEGORY), revenue, Long::sum));

        return revenueByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new CategoryRanking(e.getKey(), e.getValue()))
                .toList();
    }

    private <T> long sum(List<T> items, java.util.function.Function<T, String> statusOf, String status,
                         java.util.function.ToIntFunction<T> amountOf) {
        return items.stream().filter(i -> status.equals(statusOf.apply(i))).mapToLong(amountOf::applyAsInt).sum();
    }

    private <T> int count(List<T> items, java.util.function.Function<T, String> statusOf, String status) {
        return (int) items.stream().filter(i -> status.equals(statusOf.apply(i))).count();
    }
}
