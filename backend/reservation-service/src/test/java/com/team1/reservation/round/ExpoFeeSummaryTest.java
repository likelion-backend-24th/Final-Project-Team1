package com.team1.reservation.round;

import com.team1.reservation.round.dto.ExpoFeeSummaryResponse;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.round.service.RoundService;
import com.team1.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 목록의 유료/무료 배지 판정. 규칙은 한 줄이다 -
 * <b>예약 가능한 회차 중 참가비가 있는 회차가 하나라도 있으면 유료다.</b>
 *
 * <p>"예약 가능한" 이 핵심이다. 예약은 회차 시작 전까지만 받으므로, 유료 회차가 시작했거나
 * 삭제되어 실제로 돈 낼 회차가 남지 않으면 그 박람회는 다시 무료로 보여야 한다.
 */
class ExpoFeeSummaryTest extends IntegrationTestSupport {

    private static final int CAPACITY = 10;
    private static final int FREE = 0;
    private static final int PAID = 10000;

    private static final long EXPO_A = 901L;
    private static final long EXPO_B = 902L;

    @Autowired
    private RoundService roundService;

    @Autowired
    private RoundRepository rounds;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Instant now;

    @BeforeEach
    void setUp() {
        rounds.deleteAll();
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Long round(long expoId, int fee, Instant startsAt) {
        // 과거 회차도 만들어야 하므로 기준 시각을 startsAt 직전으로 넘겨 불변식을 통과시킨다.
        return rounds.save(Round.create(expoId, startsAt, startsAt.plusSeconds(3600),
                CAPACITY, fee, startsAt.minusSeconds(60))).getId();
    }

    private Instant future() {
        return now.plusSeconds(86400);
    }

    private Instant past() {
        return now.minusSeconds(86400);
    }

    private Map<Long, Boolean> summaries(Long... expoIds) {
        return roundService.feeSummaries(List.of(expoIds)).stream()
                .collect(Collectors.toMap(ExpoFeeSummaryResponse::expoId, ExpoFeeSummaryResponse::paid));
    }

    @Test
    @DisplayName("무료 회차만 있으면 무료")
    void freeWhenNoPaidRound() {
        round(EXPO_A, FREE, future());
        round(EXPO_A, FREE, future().plusSeconds(7200));

        assertThat(summaries(EXPO_A)).containsEntry(EXPO_A, false);
    }

    @Test
    @DisplayName("무료 4개 + 유료 1개면 유료 - 한 회차라도 걸리면 유료다")
    void paidWhenAnyRoundHasFee() {
        for (int i = 0; i < 4; i++) {
            round(EXPO_A, FREE, future().plusSeconds(3600L * i));
        }
        round(EXPO_A, PAID, future().plusSeconds(100000));

        assertThat(summaries(EXPO_A)).containsEntry(EXPO_A, true);
    }

    @Test
    @DisplayName("유료 회차가 시작해 예약을 못 받게 되면 다시 무료")
    void freeAgainWhenPaidRoundStarted() {
        round(EXPO_A, PAID, past());
        round(EXPO_A, FREE, future());

        assertThat(summaries(EXPO_A)).containsEntry(EXPO_A, false);
    }

    @Test
    @DisplayName("유료 회차를 삭제하면 다시 무료")
    void freeAgainWhenPaidRoundDeleted() {
        Long paidRoundId = round(EXPO_A, PAID, future());
        round(EXPO_A, FREE, future());

        // softDeleteIfNoReservation 은 @Modifying 이라 Transaction 을 요구한다.
        // 클래스에 @Transactional 을 걸면 삭제가 커밋되지 않아 판정 쿼리가 못 보므로 이 줄만 감싼다.
        Integer deleted = new TransactionTemplate(transactionManager)
                .execute(status -> rounds.softDeleteIfNoReservation(paidRoundId, now));
        assertThat(deleted).isEqualTo(1);

        assertThat(summaries(EXPO_A)).containsEntry(EXPO_A, false);
    }

    @Test
    @DisplayName("예약 가능한 회차가 하나도 없으면 응답에서 빠진다 - 판정 근거가 없다")
    void omittedWhenNoOpenRound() {
        round(EXPO_A, PAID, past());

        assertThat(summaries(EXPO_A)).doesNotContainKey(EXPO_A);
    }

    @Test
    @DisplayName("여러 박람회를 한 번에 판정한다")
    void summarizesManyExposAtOnce() {
        round(EXPO_A, PAID, future());
        round(EXPO_B, FREE, future());

        assertThat(summaries(EXPO_A, EXPO_B))
                .containsEntry(EXPO_A, true)
                .containsEntry(EXPO_B, false);
    }

    @Test
    @DisplayName("빈 목록은 조회 없이 빈 결과")
    void emptyInputReturnsEmpty() {
        assertThat(roundService.feeSummaries(List.of())).isEmpty();
    }
}
