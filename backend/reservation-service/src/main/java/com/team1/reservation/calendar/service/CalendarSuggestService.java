package com.team1.reservation.calendar.service;

import com.team1.reservation.calendar.dto.CalendarRoundView;
import com.team1.reservation.calendar.dto.CalendarSuggestMeta;
import com.team1.reservation.calendar.dto.ConstraintSource;
import com.team1.reservation.calendar.dto.ScheduleConstraint;
import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.round.dto.InternalRoundResponse;
import com.team1.reservation.round.service.RoundService;
import com.team1.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 캘린더 AI 일정 추천의 오케스트레이션(계약: GET /api/v1/me/calendar/suggest).
 *
 * <p>순서: 인증 -&gt; rate limit -&gt; 파라미터 검증 -&gt; (후보 회차 조회 + 제약 해석)을 병렬 실행
 * -&gt; 겹침 제거 -&gt; 응답 조립. 제약 해석은 Gemini -&gt; 정규식 -&gt; 무제약 순으로 폴백한다.
 */
@Service
public class CalendarSuggestService {

    private static final int MAX_CONSTRAINT_LENGTH = 200;

    private final CalendarSuggestRateLimiter rateLimiter;
    private final ExpoClient expoClient;
    private final RoundService roundService;
    private final GeminiConstraintParser geminiConstraintParser;
    private final ExecutorService aiTaskExecutor;

    public CalendarSuggestService(CalendarSuggestRateLimiter rateLimiter,
                                  ExpoClient expoClient,
                                  RoundService roundService,
                                  GeminiConstraintParser geminiConstraintParser,
                                  @Qualifier("aiTaskExecutor") ExecutorService aiTaskExecutor) {
        this.rateLimiter = rateLimiter;
        this.expoClient = expoClient;
        this.roundService = roundService;
        this.geminiConstraintParser = geminiConstraintParser;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    public record Result(List<CalendarRoundView> schedule, CalendarSuggestMeta meta) {
    }

    public Result suggest(AuthenticatedUser user, Instant from, Instant to, String constraint) {
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if (!rateLimiter.tryAcquire(user.userId())) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "too many calendar suggest requests");
        }
        if (constraint != null && constraint.length() > MAX_CONSTRAINT_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "constraint too long: " + constraint.length());
        }

        CompletableFuture<List<InternalRoundResponse>> candidatesFuture =
                CompletableFuture.supplyAsync(() -> fetchCandidates(from, to, true), aiTaskExecutor);
        CompletableFuture<ResolvedConstraint> constraintFuture =
                CompletableFuture.supplyAsync(() -> resolveConstraint(constraint), aiTaskExecutor);

        List<InternalRoundResponse> candidates = joinCandidates(candidatesFuture);
        ResolvedConstraint resolved = constraintFuture.join();

        List<InternalRoundResponse> pool = filterByCategory(candidates, resolved.constraint().category());
        List<InternalRoundResponse> picked = ScheduleGreedyPicker.pick(pool, resolved.constraint());

        return new Result(toViews(picked), new CalendarSuggestMeta(resolved.source(), pool.size()));
    }

    /**
     * 캘린더 기본 화면(계약: GET /api/v1/calendar/events). 로그인·제약·겹침 제거 없이,
     * 그 기간에 공개된 회차를 있는 그대로 다 보여준다 - AI 추천은 이 위에 얹는 별도 동작이다.
     */
    public List<CalendarRoundView> listEvents(Instant from, Instant to) {
        return toViews(fetchCandidates(from, to, false));
    }

    private List<CalendarRoundView> toViews(List<InternalRoundResponse> rounds) {
        Map<Long, String> titles = expoClient.titles(
                rounds.stream().map(InternalRoundResponse::expoId).distinct().toList());
        return rounds.stream()
                .map(round -> CalendarRoundView.of(round, titles.getOrDefault(round.expoId(), "")))
                .toList();
    }

    private List<InternalRoundResponse> fetchCandidates(Instant from, Instant to, boolean bookableOnly) {
        List<Long> expoIds = expoClient.publishedExpoIds();
        if (expoIds.isEmpty()) {
            return List.of();
        }
        return roundService.roundsByDate(expoIds, from, to, bookableOnly);
    }

    /**
     * 카테고리 조건은 사용자가 명시적으로 요청한 필터라, 카테고리를 모르면 후보에서 뺀다(fail-closed).
     * 조회에 실패해서 다 못 걸러도 조건 없는 것처럼 전부 보여주면, 지금 고치려는 문제(카테고리를
     * 말해도 무시되고 아무 분야나 추천되는 것)가 그대로 재현된다.
     */
    private List<InternalRoundResponse> filterByCategory(List<InternalRoundResponse> candidates, String category) {
        if (category == null || candidates.isEmpty()) {
            return candidates;
        }
        List<Long> expoIds = candidates.stream().map(InternalRoundResponse::expoId).distinct().toList();
        Map<Long, String> categoriesByExpoId = expoClient.categories(expoIds);
        return candidates.stream()
                .filter(r -> category.equals(categoriesByExpoId.get(r.expoId())))
                .toList();
    }

    /** roundsByDate가 검증 실패로 던진 ApiException(기간 상한 등)은 그대로 살려서 올린다. */
    private List<InternalRoundResponse> joinCandidates(CompletableFuture<List<InternalRoundResponse>> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "candidate lookup failed");
        }
    }

    private record ResolvedConstraint(ScheduleConstraint constraint, ConstraintSource source) {
    }

    private ResolvedConstraint resolveConstraint(String constraint) {
        if (constraint == null || constraint.isBlank()) {
            return new ResolvedConstraint(ScheduleConstraint.NONE, ConstraintSource.NONE);
        }

        ScheduleConstraint fromGemini = tryOrNull(() -> geminiConstraintParser.tryParse(constraint));
        if (fromGemini != null) {
            return new ResolvedConstraint(fromGemini, ConstraintSource.GEMINI);
        }

        ScheduleConstraint fromRegex = RegexConstraintParser.tryParse(constraint);
        if (fromRegex != null) {
            return new ResolvedConstraint(fromRegex, ConstraintSource.RULE);
        }

        return new ResolvedConstraint(ScheduleConstraint.NONE, ConstraintSource.NONE);
    }

    /** Gemini 호출 쪽에서 예상 못 한 예외가 나도 폴백(정규식)으로 넘어가야 한다. */
    private ScheduleConstraint tryOrNull(Supplier<ScheduleConstraint> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }
}
