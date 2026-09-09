package com.team1.reservation.reservation;

import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** #77 조건부 UPDATE 검증. JPQL 의 Enum 리터럴은 컴파일이 아니라 실행 시점에 깨진다. */
@Transactional // @Modifying 쿼리는 트랜잭션을 요구하고, 덤으로 Test 데이터가 롤백된다
class ExpireIfPendingTest extends IntegrationTestSupport {

    private static final Instant CREATED = Instant.parse("2026-09-09T00:00:00Z");

    @Autowired
    private ReservationRepository reservations;

    private Reservation save(String no) {
        return reservations.saveAndFlush(Reservation.create(no, 7L, 1L, 100L,
                "홍길동", "01012345678", 2, 20000, CREATED));
    }

    @Test
    @DisplayName("PENDING 은 EXPIRED 로 바뀌고 1을 반환한다")
    void expiresPending() {
        Reservation pending = save("R-EXP-0001");

        assertThat(reservations.expireIfPending(pending.getId())).isEqualTo(1);
        assertThat(reservations.findById(pending.getId()))
                .get().extracting(Reservation::getStatus).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("이미 CONFIRMED 면 0을 반환하고 상태를 건드리지 않는다")
    void skipsConfirmed() {
        Reservation confirmed = save("R-EXP-0002");
        confirmed.confirm(CREATED.plusSeconds(60));
        reservations.saveAndFlush(confirmed);

        assertThat(reservations.expireIfPending(confirmed.getId())).isZero();
        assertThat(reservations.findById(confirmed.getId()))
                .get().extracting(Reservation::getStatus).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("만료 시각을 지난 PENDING 만 후보로 나온다")
    void findsOnlyDuePending() {
        Reservation due = save("R-EXP-0003");
        Reservation confirmed = save("R-EXP-0004");
        confirmed.confirm(CREATED.plusSeconds(60));
        reservations.saveAndFlush(confirmed);

        // expires_at = CREATED + 10분. cutoff 를 그 뒤로 잡아야 후보가 된다.
        List<Reservation> candidates =
                reservations.findExpirable(CREATED.plusSeconds(900), PageRequest.of(0, 200));

        assertThat(candidates).extracting(Reservation::getId).contains(due.getId());
        assertThat(candidates).extracting(Reservation::getId).doesNotContain(confirmed.getId());
    }

    @Test
    @DisplayName("cutoff 이전의 예약은 후보에 들어가지 않는다")
    void excludesNotYetDue() {
        Reservation notYet = save("R-EXP-0005");

        List<Reservation> candidates =
                reservations.findExpirable(CREATED.plusSeconds(60), PageRequest.of(0, 200));

        assertThat(candidates).extracting(Reservation::getId).doesNotContain(notYet.getId());
    }
}
