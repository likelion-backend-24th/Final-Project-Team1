package com.team1.settlement.service;

import com.team1.ai.GeminiClient;
import com.team1.settlement.dto.CategoryRanking;
import com.team1.settlement.dto.ExpoRanking;
import com.team1.settlement.dto.SettlementBucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 정산 요약(#내부분에서 AI 더 쓰기) 검증. 합계·이상치 판정은 이 서비스가 직접 계산하고
 * Gemini 는 그 결과를 문장으로 옮기기만 한다는 전제를 확인한다 - 그래서 프롬프트에
 * 실제 계산된 사실이 들어가는지가 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementInsightServiceTest {

    @Mock
    private GeminiClient geminiClient;

    private SettlementInsightService service;

    private void setUp() {
        service = new SettlementInsightService(geminiClient);
    }

    private SettlementBucket bucket(String label, long revenue, long refund) {
        return new SettlementBucket(label, revenue, refund, revenue - refund);
    }

    @Test
    @DisplayName("Gemini 를 쓸 수 없으면 프롬프트도 안 만들고 null")
    void returnsNullWhenGeminiUnavailable() {
        setUp();
        when(geminiClient.isAvailable()).thenReturn(false);

        String result = service.summarize("2026-09-01 ~ 2026-09-30", 100000, 10000, 90000,
                List.of(), List.of(), List.of());

        assertThat(result).isNull();
        verify(geminiClient, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Gemini 응답을 그대로 요약으로 쓴다")
    void returnsGeminiSummary() {
        setUp();
        when(geminiClient.isAvailable()).thenReturn(true);
        when(geminiClient.generateJson(anyString(), anyString(), eq(SettlementInsightService.Parsed.class)))
                .thenReturn(new SettlementInsightService.Parsed("이번 달 매출은 10만원입니다."));

        String result = service.summarize("2026-09-01 ~ 2026-09-30", 100000, 10000, 90000,
                List.of(new ExpoRanking(1L, "IT 박람회", 80000)),
                List.of(new CategoryRanking("IT·전자", 80000)),
                List.of(bucket("2026-09-01", 50000, 5000), bucket("2026-09-02", 50000, 5000)));

        assertThat(result).isEqualTo("이번 달 매출은 10만원입니다.");
    }

    @Test
    @DisplayName("응답이 비었거나 실패하면 null - 화면은 숫자만 보여준다(fail-open)")
    void returnsNullWhenGeminiFailsOrBlank() {
        setUp();
        when(geminiClient.isAvailable()).thenReturn(true);
        when(geminiClient.generateJson(anyString(), anyString(), eq(SettlementInsightService.Parsed.class)))
                .thenReturn(null, new SettlementInsightService.Parsed("  "));

        assertThat(service.summarize("p", 1, 0, 1, List.of(), List.of(), List.of())).isNull();
        assertThat(service.summarize("p", 1, 0, 1, List.of(), List.of(), List.of())).isNull();
    }

    @Test
    @DisplayName("버킷 중 하나가 평균보다 크게 튀는 환불이면 특이사항으로 프롬프트에 들어간다")
    void includesAnomalyFactWhenRefundSpikes() {
        setUp();
        when(geminiClient.isAvailable()).thenReturn(true);
        when(geminiClient.generateJson(anyString(), anyString(), eq(SettlementInsightService.Parsed.class)))
                .thenReturn(new SettlementInsightService.Parsed("요약"));

        List<SettlementBucket> buckets = List.of(
                bucket("2026-09-01", 50000, 1000),
                bucket("2026-09-02", 50000, 1000),
                bucket("2026-09-03", 50000, 1000),
                bucket("2026-09-04", 50000, 1000),
                bucket("2026-09-05", 50000, 1000),
                bucket("2026-09-16", 50000, 200000)); // 확 튀는 환불

        service.summarize("2026-09-01 ~ 2026-09-30", 300000, 205000, 95000, List.of(), List.of(), buckets);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(anyString(), prompt.capture(), eq(SettlementInsightService.Parsed.class));
        assertThat(prompt.getValue()).contains("2026-09-16").contains("200,000원");
    }

    @Test
    @DisplayName("버킷이 고르면(튀는 값 없음) 특이사항 없음으로 표시한다")
    void noAnomalyWhenBucketsAreEven() {
        setUp();
        when(geminiClient.isAvailable()).thenReturn(true);
        when(geminiClient.generateJson(anyString(), anyString(), eq(SettlementInsightService.Parsed.class)))
                .thenReturn(new SettlementInsightService.Parsed("요약"));

        List<SettlementBucket> buckets = List.of(
                bucket("2026-09-01", 50000, 1000),
                bucket("2026-09-02", 50000, 1000),
                bucket("2026-09-03", 50000, 1000));

        service.summarize("2026-09-01 ~ 2026-09-30", 150000, 3000, 147000, List.of(), List.of(), buckets);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(anyString(), prompt.capture(), eq(SettlementInsightService.Parsed.class));
        assertThat(prompt.getValue()).contains("[특이사항] 없음");
    }

    @Test
    @DisplayName("당일처럼 버킷이 1~2개뿐이면 이상치 계산 자체를 건너뛴다")
    void skipsAnomalyDetectionWithTooFewBuckets() {
        setUp();
        when(geminiClient.isAvailable()).thenReturn(true);
        when(geminiClient.generateJson(anyString(), anyString(), eq(SettlementInsightService.Parsed.class)))
                .thenReturn(new SettlementInsightService.Parsed("요약"));

        service.summarize("2026-09-23", 50000, 100000, -50000, List.of(), List.of(),
                List.of(bucket("2026-09-23", 50000, 100000)));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(anyString(), prompt.capture(), eq(SettlementInsightService.Parsed.class));
        assertThat(prompt.getValue()).contains("[특이사항] 없음");
    }
}
