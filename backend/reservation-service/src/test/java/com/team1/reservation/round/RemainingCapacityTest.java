package com.team1.reservation.round;

import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.round.dto.InternalRoundResponse;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.service.RoundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class RemainingCapacityTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-09-10T02:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-09-10T08:00:00Z");

    private Round round(int capacity) {
        return Round.create(1L, STARTS, ENDS, capacity, 0, NOW);
    }

    @Test
    @DisplayName("예약이 없으면 잔여 정원은 정원과 같다")
    void remainingEqualsCapacity() {
        assertThat(round(1).remaining()).isEqualTo(1);
        assertThat(round(100).remaining()).isEqualTo(100);
        assertThat(round(9999).remaining()).isEqualTo(9999);
    }

    @Test
    @DisplayName("내부 응답의 remaining 은 Round.remaining() 을 그대로 담는다")
    void internalResponseCarriesRemaining() {
        Round round = round(50);

        InternalRoundResponse response = InternalRoundResponse.from(round);

        assertThat(response.capacity()).isEqualTo(50);
        assertThat(response.remaining()).isEqualTo(round.remaining());
        assertThat(response.startsAt()).isEqualTo(STARTS);
        assertThat(response.endsAt()).isEqualTo(ENDS);
    }

    @Test
    @DisplayName("회차 목록 조회는 starts_at 오름차순 Query 로 위임한다")
    void listByExpoDelegatesToOrderedQuery() {
        RoundRepository rounds = mock(RoundRepository.class);
        RoundService service = new RoundService(
                rounds, mock(ExpoClient.class), Clock.fixed(NOW, ZoneOffset.UTC));

        Round first = round(10);
        Round second = round(20);
        when(rounds.findByExpoIdOrderByStartsAtAsc(1L)).thenReturn(List.of(first, second));

        assertThat(service.listByExpo(1L)).containsExactly(first, second);
        verify(rounds).findByExpoIdOrderByStartsAtAsc(1L);
    }

    @Test
    @DisplayName("회차가 없으면 빈 목록을 반환한다")
    void listByExpoReturnsEmptyList() {
        RoundRepository rounds = mock(RoundRepository.class);
        RoundService service = new RoundService(
                rounds, mock(ExpoClient.class), Clock.fixed(NOW, ZoneOffset.UTC));

        when(rounds.findByExpoIdOrderByStartsAtAsc(99L)).thenReturn(List.of());

        assertThat(service.listByExpo(99L)).isEmpty();
    }
}
