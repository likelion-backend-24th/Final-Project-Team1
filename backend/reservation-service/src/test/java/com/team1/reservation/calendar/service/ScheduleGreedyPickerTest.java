package com.team1.reservation.calendar.service;

import com.team1.reservation.calendar.dto.ScheduleConstraint;
import com.team1.reservation.round.dto.InternalRoundResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleGreedyPickerTest {

    private static InternalRoundResponse round(long roundId, long expoId, String startsAt, String endsAt) {
        return new InternalRoundResponse(roundId, expoId, 1,
                Instant.parse(startsAt), Instant.parse(endsAt), 100, 50, 10000);
    }

    @Test
    @DisplayName("겹치는 두 회차 중 하나만 고른다")
    void picksOneOfOverlappingRounds() {
        InternalRoundResponse a = round(1L, 10L, "2026-09-19T05:00:00Z", "2026-09-19T08:00:00Z");
        InternalRoundResponse b = round(2L, 20L, "2026-09-19T06:00:00Z", "2026-09-19T09:00:00Z");

        List<InternalRoundResponse> result = ScheduleGreedyPicker.pick(List.of(a, b), ScheduleConstraint.NONE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roundId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 박람회의 빠른 회차가 겹쳐서 빠져도, 늦은 회차가 대신 들어간다")
    void fallsBackToLaterRoundOfSameExpoWhenEarliestConflicts() {
        InternalRoundResponse bRound = round(1L, 20L, "2026-09-19T05:00:00Z", "2026-09-19T07:00:00Z");
        InternalRoundResponse aRound1 = round(2L, 10L, "2026-09-19T06:00:00Z", "2026-09-19T09:00:00Z");
        InternalRoundResponse aRound2 = round(3L, 10L, "2026-09-19T10:00:00Z", "2026-09-19T12:00:00Z");

        List<InternalRoundResponse> result = ScheduleGreedyPicker.pick(
                List.of(bRound, aRound1, aRound2), ScheduleConstraint.NONE);

        assertThat(result).extracting(InternalRoundResponse::roundId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("같은 박람회 회차 중 시간이 안 겹쳐도 하나만 고른다(중복 방지)")
    void dedupsSameExpoEvenWhenNotOverlapping() {
        InternalRoundResponse round1 = round(1L, 10L, "2026-09-19T05:00:00Z", "2026-09-19T07:00:00Z");
        InternalRoundResponse round2 = round(2L, 10L, "2026-09-20T05:00:00Z", "2026-09-20T07:00:00Z");

        List<InternalRoundResponse> result = ScheduleGreedyPicker.pick(
                List.of(round1, round2), ScheduleConstraint.NONE);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("[start, end) 반열린 구간 - 딱 붙은 회차는 안 겹치는 걸로 본다")
    void backToBackRoundsDoNotOverlap() {
        InternalRoundResponse first = round(1L, 10L, "2026-09-19T05:00:00Z", "2026-09-19T07:00:00Z");
        InternalRoundResponse second = round(2L, 20L, "2026-09-19T07:00:00Z", "2026-09-19T09:00:00Z");

        List<InternalRoundResponse> result = ScheduleGreedyPicker.pick(
                List.of(first, second), ScheduleConstraint.NONE);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("KST 기준으로 시각 제약을 적용한다 - UTC 그대로 비교하면 틀린다")
    void appliesConstraintInKst() {
        // UTC 06:00 = KST 15:00 -> "14시 이후" 통과
        InternalRoundResponse afternoon = round(1L, 10L, "2026-09-19T06:00:00Z", "2026-09-19T08:00:00Z");
        // UTC 01:00 = KST 10:00 -> "14시 이후" 불통과
        InternalRoundResponse morning = round(2L, 20L, "2026-09-19T01:00:00Z", "2026-09-19T03:00:00Z");

        List<InternalRoundResponse> result = ScheduleGreedyPicker.pick(
                List.of(afternoon, morning), new ScheduleConstraint(14));

        assertThat(result).extracting(InternalRoundResponse::roundId).containsExactly(1L);
    }
}
