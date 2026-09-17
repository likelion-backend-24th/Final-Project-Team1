package com.team1.reservation.round;

import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.round.dto.InternalRoundResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 회차 번호(1회차·2회차)는 저장하지 않고 조회할 때마다 날짜순으로 다시 매긴다.
 *
 * <p>핵심은 마지막 두 개다 - 목록으로 매긴 번호와 단건으로 센 번호가 같아야 하고,
 * 앞 회차를 삭제하면 뒤 번호가 당겨져야 한다. 두 경로가 어긋나면 화면마다 다른 번호가 보인다.
 */
class RoundSequenceTest extends IntegrationTestSupport {

    private static final long EXPO_ID = 801L;
    private static final int CAPACITY = 10;

    @Autowired
    private RoundService roundService;

    @Autowired
    private RoundRepository rounds;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Instant now;

    @BeforeEach
    void setUp() {
        // reservations.round_id 에 FK 가 걸려 있어 예약을 먼저 지워야 회차가 지워진다.
        // Container 를 공유하므로 앞선 Test 클래스가 남긴 예약이 있을 수 있다.
        reservations.deleteAll();
        rounds.deleteAll();
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Round save(Instant startsAt) {
        return rounds.save(Round.create(EXPO_ID, startsAt, startsAt.plusSeconds(3600),
                CAPACITY, 0, startsAt.minusSeconds(60)));
    }

    private Instant day(int n) {
        return now.plus(n, ChronoUnit.DAYS);
    }

    @Test
    @DisplayName("등록 순서가 아니라 날짜순으로 번호를 매긴다")
    void numbersByDateNotByCreation() {
        Round third = save(day(3));
        Round first = save(day(1));
        Round second = save(day(2));

        assertThat(InternalRoundResponse.listOf(roundService.listByExpo(EXPO_ID)))
                .extracting(InternalRoundResponse::roundId, InternalRoundResponse::sequence)
                .containsExactly(
                        tuple(first.getId(), 1),
                        tuple(second.getId(), 2),
                        tuple(third.getId(), 3));
    }

    @Test
    @DisplayName("시작 시각이 같으면 id 순으로 갈라 번호가 흔들리지 않는다")
    void breaksTiesById() {
        Round earlier = save(day(1));
        Round later = save(day(1));

        assertThat(InternalRoundResponse.listOf(roundService.listByExpo(EXPO_ID)))
                .extracting(InternalRoundResponse::roundId)
                .containsExactly(earlier.getId(), later.getId());
        assertThat(roundService.sequenceOf(later)).isEqualTo(2);
    }

    @Test
    @DisplayName("단건으로 센 번호가 목록 번호와 같다 - 체크인 화면과 상세 화면이 어긋나면 안 된다")
    void singleLookupMatchesList() {
        save(day(1));
        Round second = save(day(2));
        save(day(3));

        List<InternalRoundResponse> listed = InternalRoundResponse.listOf(roundService.listByExpo(EXPO_ID));
        int fromList = listed.stream()
                .filter(r -> r.roundId().equals(second.getId()))
                .findFirst().orElseThrow()
                .sequence();

        assertThat(roundService.sequenceOf(second)).isEqualTo(fromList).isEqualTo(2);
    }

    @Test
    @DisplayName("앞 회차를 삭제하면 뒤 회차 번호가 당겨진다")
    void shiftsDownWhenEarlierRoundIsDeleted() {
        Round first = save(day(1));
        Round second = save(day(2));

        assertThat(roundService.sequenceOf(second)).isEqualTo(2);

        // softDeleteIfNoReservation 은 @Modifying 이라 Transaction 을 요구한다.
        new TransactionTemplate(transactionManager)
                .execute(status -> rounds.softDeleteIfNoReservation(first.getId(), now));

        assertThat(roundService.sequenceOf(second)).isEqualTo(1);
        assertThat(InternalRoundResponse.listOf(roundService.listByExpo(EXPO_ID)))
                .extracting(InternalRoundResponse::sequence)
                .containsExactly(1);
    }
}
