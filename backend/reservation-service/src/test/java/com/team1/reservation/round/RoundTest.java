package com.team1.reservation.round;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.round.entity.Round;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-09-10T02:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-09-10T08:00:00Z");

    @Test
    @DisplayName("정상 입력이면 회차가 만들어지고 잔여 정원은 정원과 같다")
    void createsRound() {
        Round round = Round.create(1L, STARTS, ENDS, 100, 0, NOW);

        assertThat(round.getExpoId()).isEqualTo(1L);
        assertThat(round.getCapacity()).isEqualTo(100);
        assertThat(round.remaining()).isEqualTo(100);
        assertThat(round.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("정원이 1 미만이면 거절한다")
    void rejectsCapacityBelowOne() {
        assertThatThrownBy(() -> Round.create(1L, STARTS, ENDS, 0, 0, NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("시작 시각이 등록 시점보다 과거면 거절한다")
    void rejectsPastStart() {
        Instant past = NOW.minusSeconds(1);

        assertThatThrownBy(() -> Round.create(1L, past, ENDS, 10, 0, NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("종료 시각이 시작 시각과 같거나 빠르면 거절한다")
    void rejectsEndNotAfterStart() {
        assertThatThrownBy(() -> Round.create(1L, STARTS, STARTS, 10, 0, NOW))
                .isInstanceOf(ApiException.class);

        assertThatThrownBy(() -> Round.create(1L, STARTS, STARTS.minusSeconds(1), 10, 0, NOW))
                .isInstanceOf(ApiException.class);
    }
}
