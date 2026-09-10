package com.team1.reservation.reservation;

import com.team1.reservation.reservation.dto.ReservationSummaryResponse;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.service.ReservationQueryService;
import com.team1.reservation.reservation.support.QueryTestFixture;
import com.team1.reservation.round.entity.Round;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReservationSummaryTest extends QueryTestFixture {

    @Autowired
    private ReservationQueryService service;

    @Test
    @DisplayName("회차별 확정·취소 인원이 실제 데이터와 일치한다")
    void aggregatesByRound() {
        Long roundA = givenRound(EXPO_ID, 100);
        Long roundB = givenRound(EXPO_ID, 50);

        givenReservation(EXPO_ID, roundA, 1L, 2, ReservationStatus.CONFIRMED);
        givenReservation(EXPO_ID, roundA, 2L, 3, ReservationStatus.CONFIRMED);
        givenReservation(EXPO_ID, roundA, 3L, 1, ReservationStatus.CANCELLED);
        givenReservation(EXPO_ID, roundB, 4L, 4, ReservationStatus.CONFIRMED);

        List<ReservationSummaryResponse> summary = service.summary(EXPO_ID);

        assertThat(summary).hasSize(2);
        assertThat(summary)
                .extracting(ReservationSummaryResponse::roundId,
                        ReservationSummaryResponse::capacity,
                        ReservationSummaryResponse::confirmed,
                        ReservationSummaryResponse::cancelled)
                .contains(tuple(roundA, 100, 5, 1), tuple(roundB, 50, 4, 0));
    }

    @Test
    @DisplayName("회차 시각을 함께 내려준다 - 박람회-Service 의 마감 정렬이 이 값을 쓴다")
    void exposesRoundSchedule() {
        Long roundId = givenRound(EXPO_ID, 100);

        ReservationSummaryResponse row = service.summary(EXPO_ID).get(0);

        Round round = rounds.findById(roundId).orElseThrow();
        assertThat(row.startsAt()).isEqualTo(round.getStartsAt());
        assertThat(row.endsAt()).isEqualTo(round.getEndsAt());
        assertThat(row.endsAt()).isAfter(row.startsAt());
    }

    @Test
    @DisplayName("건수가 아니라 인원 합이다 - 2명 + 3명 예약이면 확정 5")
    void countsHeadcountNotRows() {
        Long roundId = givenRound(EXPO_ID, 100);
        givenReservation(EXPO_ID, roundId, 1L, 2, ReservationStatus.CONFIRMED);
        givenReservation(EXPO_ID, roundId, 2L, 3, ReservationStatus.CONFIRMED);

        assertThat(service.summary(EXPO_ID).get(0).confirmed()).isEqualTo(5);
    }

    @Test
    @DisplayName("예약이 하나도 없는 회차도 0 으로 나온다 - 빠뜨리면 화면에서 회차가 사라진다")
    void includesRoundsWithoutReservations() {
        Long empty = givenRound(EXPO_ID, 30);

        List<ReservationSummaryResponse> summary = service.summary(EXPO_ID);

        assertThat(summary)
                .extracting(ReservationSummaryResponse::roundId,
                        ReservationSummaryResponse::capacity,
                        ReservationSummaryResponse::confirmed,
                        ReservationSummaryResponse::cancelled)
                .containsExactly(tuple(empty, 30, 0, 0));
    }

    @Test
    @DisplayName("회차가 없는 박람회는 빈 배열이다")
    void emptyForExpoWithoutRounds() {
        assertThat(service.summary(999L)).isEmpty();
    }

    @Test
    @DisplayName("다른 박람회의 예약이 섞이지 않는다")
    void isolatesByExpo() {
        Long mine = givenRound(EXPO_ID, 100);
        Long theirs = givenRound(OTHER_EXPO_ID, 100);
        givenReservation(EXPO_ID, mine, 1L, 2, ReservationStatus.CONFIRMED);
        givenReservation(OTHER_EXPO_ID, theirs, 2L, 7, ReservationStatus.CONFIRMED);

        List<ReservationSummaryResponse> summary = service.summary(EXPO_ID);

        assertThat(summary)
                .extracting(ReservationSummaryResponse::roundId, ReservationSummaryResponse::confirmed)
                .containsExactly(tuple(mine, 2));
    }

    @Test
    @DisplayName("PENDING·EXPIRED 는 확정에도 취소에도 들어가지 않는다")
    void countsOnlyConfirmedAndCancelled() {
        Long roundId = givenRound(EXPO_ID, 100);
        givenReservation(EXPO_ID, roundId, 1L, 2, ReservationStatus.PENDING);
        givenReservation(EXPO_ID, roundId, 2L, 3, ReservationStatus.EXPIRED);
        givenReservation(EXPO_ID, roundId, 3L, 1, ReservationStatus.CONFIRMED);

        assertThat(service.summary(EXPO_ID))
                .extracting(ReservationSummaryResponse::roundId, ReservationSummaryResponse::confirmed)
                .containsExactly(tuple(roundId, 1));
    }
}
