package com.team1.reservation.round;

import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.round.dto.UpdateRoundRequest;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.round.service.RoundService;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S9-2 회차 수정의 시각 경계·소유권·불변식. 시계를 고정해야 하므로 단위 Test 다.
 * 조건부 UPDATE 가 실제로 예약을 막는지는 {@code UpdateRoundServiceTest} 가 실 DB 로 본다.
 */
class UpdateRoundValidationTest {

    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");
    private static final Long EXPO_ID = 1L;
    private static final Long ROUND_ID = 10L;

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(7L, "ORGANIZER");
    private static final AuthenticatedUser OTHER_ORGANIZER = new AuthenticatedUser(99L, "ORGANIZER");

    private RoundRepository rounds;
    private ExpoClient expoClient;
    private RoundService service;

    @BeforeEach
    void setUp() {
        rounds = mock(RoundRepository.class);
        expoClient = mock(ExpoClient.class);
        service = new RoundService(rounds, expoClient, Clock.fixed(NOW, ZoneOffset.UTC));

        when(expoClient.getExpo(EXPO_ID)).thenReturn(new ExpoSummary(EXPO_ID, OWNER.userId(), "PUBLISHED"));
    }

    /** startsAt 이 기준 시각보다 미래인 회차. */
    private Round roundStartingAt(Instant startsAt) {
        return Round.create(EXPO_ID, startsAt, startsAt.plusSeconds(3600), 10, 10000,
                startsAt.minusSeconds(60));
    }

    private void givenRound(Round round) {
        when(rounds.findById(ROUND_ID)).thenReturn(Optional.of(round));
    }

    private UpdateRoundRequest request() {
        return new UpdateRoundRequest(NOW.plusSeconds(86400), NOW.plusSeconds(90000), 50, 20000);
    }

    @Test
    @DisplayName("이미 시작한 회차는 수정할 수 없다 (409) - UPDATE 를 시도조차 하지 않는다")
    void rejectsStartedRound() {
        givenRound(roundStartingAt(NOW.minusSeconds(1)));

        assertThatThrownBy(() -> service.update(EXPO_ID, ROUND_ID, OWNER, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ROUND_ALREADY_STARTED));

        verify(rounds, never()).updateIfNoReservation(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("시작 1초 전이면 아직 수정할 수 있다 (경계)")
    void allowsJustBeforeStart() {
        givenRound(roundStartingAt(NOW.plusSeconds(1)));
        when(rounds.updateIfNoReservation(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(1);
        when(rounds.findById(ROUND_ID))
                .thenReturn(Optional.of(roundStartingAt(NOW.plusSeconds(1))));

        assertThat(service.update(EXPO_ID, ROUND_ID, OWNER, request())).isNotNull();
    }

    @Test
    @DisplayName("새 시작 시각이 과거면 400 - 등록과 같은 불변식을 쓴다")
    void rejectsPastStartsAt() {
        givenRound(roundStartingAt(NOW.plusSeconds(86400)));
        UpdateRoundRequest past =
                new UpdateRoundRequest(NOW.minusSeconds(60), NOW.plusSeconds(3600), 50, 20000);

        assertThatThrownBy(() -> service.update(EXPO_ID, ROUND_ID, OWNER, past))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(rounds, never()).updateIfNoReservation(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("endsAt 이 startsAt 보다 빠르면 400")
    void rejectsInvertedRange() {
        givenRound(roundStartingAt(NOW.plusSeconds(86400)));
        UpdateRoundRequest inverted =
                new UpdateRoundRequest(NOW.plusSeconds(90000), NOW.plusSeconds(86400), 50, 20000);

        assertThatThrownBy(() -> service.update(EXPO_ID, ROUND_ID, OWNER, inverted))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("정원이 1 미만이면 400")
    void rejectsZeroCapacity() {
        givenRound(roundStartingAt(NOW.plusSeconds(86400)));
        UpdateRoundRequest zero =
                new UpdateRoundRequest(NOW.plusSeconds(86400), NOW.plusSeconds(90000), 0, 20000);

        assertThatThrownBy(() -> service.update(EXPO_ID, ROUND_ID, OWNER, zero))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("다른 주최자면 403 이고 회차를 읽지도 않는다")
    void rejectsOtherOrganizer() {
        assertThatThrownBy(() -> service.update(EXPO_ID, ROUND_ID, OTHER_ORGANIZER, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(rounds, never()).updateIfNoReservation(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("다른 박람회의 회차 번호면 404")
    void rejectsRoundOfAnotherExpo() {
        Round otherExpoRound = Round.create(2L, NOW.plusSeconds(86400), NOW.plusSeconds(90000),
                10, 10000, NOW);
        givenRound(otherExpoRound);

        assertThatThrownBy(() -> service.update(EXPO_ID, ROUND_ID, OWNER, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
