package com.team1.reservation.reservation;

import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationExpiryService;
import com.team1.reservation.reservation.service.ReservationExpiryWriter;
import com.team1.reservation.round.repository.RoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #77 결제대기 만료. 정원 반환이 전이 1회당 정확히 1회인지가 핵심이다. */
class ReservationExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T04:00:00Z");
    private static final Duration GRACE = Duration.ofMinutes(2);
    private static final Long ROUND_ID = 7L;
    private static final int HEADCOUNT = 2;

    private ReservationRepository reservations;
    private RoundRepository rounds;
    private ReservationExpiryService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        rounds = mock(RoundRepository.class);
        ReservationExpiryWriter writer = new ReservationExpiryWriter(reservations, rounds);
        service = new ReservationExpiryService(reservations, writer,
                Clock.fixed(NOW, ZoneOffset.UTC), GRACE, 200);
    }

    /** id 는 영속화될 때 붙으므로, Repository 가 Mock 인 여기서는 직접 심어 준다. */
    private Reservation candidate(Long id) {
        Reservation reservation = Reservation.create("R-4K7Q-W2M" + id, ROUND_ID, 1L, 100L,
                "홍길동", "01012345678", HEADCOUNT, 20000, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private void givenCandidates(Reservation... candidates) {
        when(reservations.findExpirable(any(), any())).thenReturn(List.of(candidates));
    }

    @Test
    @DisplayName("유예시간을 뺀 시각을 기준으로 후보를 찾는다")
    void appliesGracePeriodToCutoff() {
        givenCandidates();

        service.expireDue();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(reservations).findExpirable(cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue()).isEqualTo(NOW.minus(GRACE));
    }

    @Test
    @DisplayName("전이에 성공하면 정원을 돌려준다")
    void releasesCapacityOnExpiry() {
        givenCandidates(candidate(1L));
        when(reservations.expireIfPending(1L)).thenReturn(1);

        assertThat(service.expireDue()).isEqualTo(1);
        verify(rounds).release(ROUND_ID, HEADCOUNT);
    }

    @Test
    @DisplayName("결제 승인이 먼저 이기면 정원을 돌려주지 않는다 - 오버부킹을 막는 지점이다")
    void doesNotReleaseWhenPaymentWonTheRace() {
        givenCandidates(candidate(1L));
        // 조회와 UPDATE 사이에 확정돼서 조건부 UPDATE 가 0 행을 바꾼 상황이다.
        when(reservations.expireIfPending(1L)).thenReturn(0);

        assertThat(service.expireDue()).isZero();
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지는 계속 만료시킨다")
    void keepsGoingAfterOneFailure() {
        givenCandidates(candidate(1L), candidate(2L), candidate(3L));
        when(reservations.expireIfPending(1L)).thenReturn(1);
        when(reservations.expireIfPending(2L)).thenThrow(new RuntimeException("db down"));
        when(reservations.expireIfPending(3L)).thenReturn(1);

        assertThat(service.expireDue()).isEqualTo(2);
        verify(reservations).expireIfPending(eq(3L));
    }
}
