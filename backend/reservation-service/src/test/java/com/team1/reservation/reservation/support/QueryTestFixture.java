package com.team1.reservation.reservation.support;

import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * #84 조회 Test 의 공통 데이터 준비. 집계 Query 를 검증하려면 실제 group by 가 돌아야 하므로
 * Mock 이 아니라 실제 MySQL 위에 행을 넣는다.
 */
public abstract class QueryTestFixture extends IntegrationTestSupport {

    protected static final Long EXPO_ID = 1L;
    protected static final Long OTHER_EXPO_ID = 2L;

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected ReservationRepository reservations;

    @Autowired
    protected RoundRepository rounds;

    protected Instant now;

    @BeforeEach
    void resetData() {
        reservations.deleteAll();
        rounds.deleteAll();
        now = Instant.now();
    }

    protected Long givenRound(Long expoId, int capacity) {
        return rounds.save(Round.create(expoId,
                now.plusSeconds(86400), now.plusSeconds(90000), capacity, 10000, now)).getId();
    }

    /** 예약 1건을 원하는 상태로 만들어 저장한다. */
    protected Reservation givenReservation(Long expoId, Long roundId, long userId,
                                           int headcount, ReservationStatus status) {
        String no = String.format("R-TEST-%04d", SEQ.incrementAndGet());
        Reservation reservation = Reservation.create(no, roundId, expoId, userId,
                "예약자" + userId, "0101234" + String.format("%04d", userId),
                headcount, headcount * 10000, now);

        switch (status) {
            case PENDING -> { /* 생성 직후가 PENDING 이다 */ }
            case CONFIRMED -> reservation.confirm(now);
            case CANCELLED -> reservation.cancel(now);
            case EXPIRED -> reservation.expire(now.plus(Reservation.PAYMENT_WINDOW));
        }
        return reservations.save(reservation);
    }
}
