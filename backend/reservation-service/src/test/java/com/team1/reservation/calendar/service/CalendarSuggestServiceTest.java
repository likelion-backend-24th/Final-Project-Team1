package com.team1.reservation.calendar.service;

import com.team1.reservation.calendar.dto.ConstraintSource;
import com.team1.reservation.calendar.dto.ScheduleConstraint;
import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.round.dto.InternalRoundResponse;
import com.team1.reservation.round.service.RoundService;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * 캘린더 추천에 카테고리 조건을 얹는 변경(#캘린더 추천이 카테고리를 무시하던 문제) 검증.
 * 시간 조건(ScheduleGreedyPickerTest)과 별개로, "카테고리를 먼저 거른 뒤 그 안에서 시간이
 * 안 겹치는 조합을 고른다"는 순서 자체를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class CalendarSuggestServiceTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(1L, "USER");
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-31T23:59:59Z");

    @Mock
    private CalendarSuggestRateLimiter rateLimiter;
    @Mock
    private ExpoClient expoClient;
    @Mock
    private RoundService roundService;
    @Mock
    private GeminiConstraintParser geminiConstraintParser;

    private ExecutorService aiTaskExecutor;
    private CalendarSuggestService service;

    @BeforeEach
    void setUp() {
        aiTaskExecutor = Executors.newFixedThreadPool(2);
        service = new CalendarSuggestService(rateLimiter, expoClient, roundService,
                geminiConstraintParser, aiTaskExecutor);

        when(rateLimiter.tryAcquire(USER.userId())).thenReturn(true);
        when(expoClient.publishedExpoIds()).thenReturn(List.of(1L, 2L));
        when(expoClient.titles(any())).thenReturn(Map.of(1L, "IT 박람회", 2L, "문화 박람회"));
    }

    private InternalRoundResponse round(long roundId, long expoId, String startsAt) {
        Instant start = Instant.parse(startsAt);
        return new InternalRoundResponse(roundId, expoId, 1, start, start.plus(2, ChronoUnit.HOURS), 100, 100, 0);
    }

    @Test
    @DisplayName("카테고리 조건이 있으면 다른 카테고리 후보는 추천에서 빠진다")
    void filtersCandidatesByCategory() {
        InternalRoundResponse itRound = round(10L, 1L, "2026-10-10T02:00:00Z");
        InternalRoundResponse cultureRound = round(20L, 2L, "2026-10-10T05:00:00Z");
        when(roundService.roundsByDate(any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(itRound, cultureRound));
        when(geminiConstraintParser.tryParse("IT 박람회만"))
                .thenReturn(new ScheduleConstraint(null, "IT·전자"));
        when(expoClient.categories(any())).thenReturn(Map.of(1L, "IT·전자", 2L, "문화·예술"));

        CalendarSuggestService.Result result = service.suggest(USER, FROM, TO, "IT 박람회만");

        assertThat(result.schedule()).extracting("roundId").containsExactly(10L);
        assertThat(result.meta().constraintSource()).isEqualTo(ConstraintSource.GEMINI);
        // 후보 풀도 카테고리로 거른 뒤(1건) 기준이어야 한다 - 원래 후보 2건이 아니다.
        assertThat(result.meta().candidateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("카테고리 조건이 없으면 모든 후보를 그대로 쓴다")
    void keepsAllCandidatesWithoutCategoryConstraint() {
        InternalRoundResponse itRound = round(10L, 1L, "2026-10-10T02:00:00Z");
        InternalRoundResponse cultureRound = round(20L, 2L, "2026-10-12T05:00:00Z");
        when(roundService.roundsByDate(any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(itRound, cultureRound));
        when(geminiConstraintParser.tryParse(any())).thenReturn(null);

        CalendarSuggestService.Result result = service.suggest(USER, FROM, TO, "아무 말이나");

        assertThat(result.schedule()).extracting("roundId").containsExactlyInAnyOrder(10L, 20L);
        assertThat(result.meta().candidateCount()).isEqualTo(2);
    }
}
