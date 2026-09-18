package com.team1.reservation.round;

import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.round.dto.InternalRoundResponse;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.round.service.RoundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 계약 2 roundsByDate. 날짜로 걸러진 부분 목록이라도 그 박람회의 살아있는 회차 전체를
 * 기준으로 번호를 매겨야 한다 - InternalRoundResponse.listOf() 를 여기서 쓰면 안 되는 이유와 같다.
 */
class RoundsByDateTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    private RoundRepository rounds;
    private RoundService service;

    @BeforeEach
    void setUp() {
        rounds = mock(RoundRepository.class);
        ExpoClient expoClient = mock(ExpoClient.class);
        service = new RoundService(rounds, expoClient, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Round round(Long id, Long expoId, String startsAt, String endsAt) {
        Round round = Round.create(expoId, Instant.parse(startsAt), Instant.parse(endsAt), 100, 10000, NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    @Test
    @DisplayName("날짜로 걸러 2회차만 나와도, 그 박람회 전체(1~3회차) 기준으로 번호를 다시 매긴다")
    void reSequencesAgainstFullExpoNotThePartialResult() {
        Round round2 = round(2L, 1L, "2026-09-19T02:00:00Z", "2026-09-19T08:00:00Z");
        Instant from = Instant.parse("2026-09-19T00:00:00Z");
        Instant to = Instant.parse("2026-09-19T23:59:59Z");

        // 날짜 조건에 걸리는 건 2회차 하나뿐이다.
        when(rounds.findByExpoIdInAndDateRange(Set.of(1L), from, to, false, NOW))
                .thenReturn(List.of(round2));

        // 그런데 이 박람회는 실제로 회차가 3개다 - 번호는 이 전체를 기준으로 매겨야 한다.
        Round round1 = round(1L, 1L, "2026-09-10T02:00:00Z", "2026-09-10T08:00:00Z");
        Round round3 = round(3L, 1L, "2026-09-28T02:00:00Z", "2026-09-28T08:00:00Z");
        when(rounds.findByExpoIdInAndDeletedAtIsNull(Set.of(1L)))
                .thenReturn(List.of(round1, round2, round3));

        List<InternalRoundResponse> result = service.roundsByDate(Set.of(1L), from, to, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roundId()).isEqualTo(2L);
        assertThat(result.get(0).expoId()).isEqualTo(1L);
        // listOf() 식으로 부분 목록 순서에만 매겼다면 여기가 1이 되어 상세 화면과 어긋난다.
        assertThat(result.get(0).sequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("bookableOnly·from·to·now 를 그대로 리포지토리 호출에 전달한다")
    void passesQueryParamsThrough() {
        Instant from = Instant.parse("2026-09-19T00:00:00Z");
        Instant to = Instant.parse("2026-09-19T23:59:59Z");
        when(rounds.findByExpoIdInAndDateRange(eq(Set.of(1L)), eq(from), eq(to), anyBoolean(), eq(NOW)))
                .thenReturn(List.of());
        when(rounds.findByExpoIdInAndDeletedAtIsNull(Set.of())).thenReturn(List.of());

        service.roundsByDate(Set.of(1L), from, to, true);

        verify(rounds).findByExpoIdInAndDateRange(Set.of(1L), from, to, true, NOW);
    }
}
