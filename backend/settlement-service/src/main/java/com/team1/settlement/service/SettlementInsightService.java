package com.team1.settlement.service;

import com.team1.ai.GeminiClient;
import com.team1.settlement.dto.CategoryRanking;
import com.team1.settlement.dto.ExpoRanking;
import com.team1.settlement.dto.SettlementBucket;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 정산 기간을 한글 한두 문장으로 요약한다(계약 밖 - 있으면 보여주고 없어도 화면은 그대로 돈다).
 *
 * <p>숫자 계산(합계·이상치 판정)은 전부 여기 자바 코드가 하고, Gemini 는 그 결과를
 * 자연스러운 문장으로 옮기는 역할만 한다 - LLM 에게 합산·통계를 직접 시키면 틀릴 수 있어서다.
 */
@Service
public class SettlementInsightService {

    private static final String FEATURE = "settlement-summary";

    /** 이 값의 표준편차 배수를 넘는 환불만 "특이사항"으로 취급한다. 잡음(작은 편차)까지 짚으면 매번 뭔가 있는 것처럼 보인다. */
    private static final double ANOMALY_STDDEV_MULTIPLIER = 2.0;
    /** 평균·표준편차가 의미를 가지려면 최소 이만큼의 구간은 있어야 한다(당일=버킷 1개라 계산 자체가 안 됨). */
    private static final int MIN_BUCKETS_FOR_ANOMALY = 3;

    private final GeminiClient geminiClient;

    public SettlementInsightService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    record Parsed(String summary) {
    }

    public String summarize(String periodLabel, long totalRevenue, long totalRefund, long netRevenue,
                            List<ExpoRanking> topExpos, List<CategoryRanking> topCategories,
                            List<SettlementBucket> buckets) {
        if (!geminiClient.isAvailable()) {
            return null;
        }
        String prompt = buildPrompt(periodLabel, totalRevenue, totalRefund, netRevenue,
                topExpos, topCategories, findAnomaly(buckets));
        Parsed parsed = geminiClient.generateJson(FEATURE, prompt, Parsed.class);
        return parsed == null || parsed.summary() == null || parsed.summary().isBlank() ? null : parsed.summary().trim();
    }

    /**
     * 환불이 평균보다 눈에 띄게 튄 구간 하나를 찾아 사실 문장으로 만든다.
     * 버킷이 너무 적거나(당일) 편차가 없으면(전부 0원 등) null - "특이사항 없음" 으로 취급된다.
     */
    private String findAnomaly(List<SettlementBucket> buckets) {
        if (buckets == null || buckets.size() < MIN_BUCKETS_FOR_ANOMALY) {
            return null;
        }
        double mean = buckets.stream().mapToLong(SettlementBucket::refund).average().orElse(0);
        double variance = buckets.stream()
                .mapToDouble(b -> Math.pow(b.refund() - mean, 2))
                .average().orElse(0);
        double stddev = Math.sqrt(variance);
        if (stddev <= 0) {
            return null;
        }

        SettlementBucket worst = buckets.stream()
                .max((a, b) -> Long.compare(a.refund(), b.refund()))
                .orElse(null);
        if (worst == null || worst.refund() <= mean + ANOMALY_STDDEV_MULTIPLIER * stddev) {
            return null;
        }
        return "%s 환불이 %,d원으로 그 기간 평균(%,d원)보다 크게 높았다".formatted(
                worst.label(), worst.refund(), Math.round(mean));
    }

    private String buildPrompt(String periodLabel, long totalRevenue, long totalRefund, long netRevenue,
                               List<ExpoRanking> topExpos, List<CategoryRanking> topCategories, String anomaly) {
        String topExpo = topExpos.isEmpty() ? "없음" : "%s (%,d원)".formatted(topExpos.get(0).title(), topExpos.get(0).revenue());
        String topCategory = topCategories.isEmpty() ? "없음" : "%s (%,d원)".formatted(topCategories.get(0).category(), topCategories.get(0).revenue());

        return """
                아래 정산 데이터를 보고 전체관리자용 한글 요약을 2문장 이내로 쓰세요.
                - 첫 문장: 이 기간의 매출·환불 규모와 매출 1위 카테고리를 자연스럽게 서술하세요.
                - 특이사항이 "없음"이 아니면 둘째 문장에 그 사실을 그대로 반영하세요. "없음"이면 둘째 문장을 쓰지 마세요.
                - 숫자는 아래 주어진 값만 그대로 쓰고, 새로 계산하거나 추측하지 마세요.
                - 조언하거나 원인을 추측하지 말고 사실만 담백하게 쓰세요.

                [기간] %s
                [총매출] %,d원
                [환불] %,d원
                [순매출] %,d원
                [매출 1위 카테고리] %s
                [매출 1위 박람회] %s
                [특이사항] %s

                아래 형식의 JSON으로만 응답하세요: {"summary": "..."}
                """.formatted(periodLabel, totalRevenue, totalRefund, netRevenue, topCategory, topExpo,
                anomaly == null ? "없음" : anomaly);
    }
}
