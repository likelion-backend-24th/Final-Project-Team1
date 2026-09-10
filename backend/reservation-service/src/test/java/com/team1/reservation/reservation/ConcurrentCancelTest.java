package com.team1.reservation.reservation;

import com.team1.payment.PaymentService;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationCancelService;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.support.IntegrationTestSupport;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #83 의 핵심 검증. 같은 예약에 취소가 동시에 들어와도 정원 반환은 정확히 1회여야 한다.
 *
 * <p>조건부 UPDATE 가 없으면 두 Transaction 이 모두 PENDING 을 읽고 각자 정원을 돌려줘
 * 회차가 실제보다 비어 보이고, 결국 정원을 넘겨 예약을 받게 된다.
 */
class ConcurrentCancelTest extends IntegrationTestSupport {

    private static final int CAPACITY = 10;
    private static final int HEADCOUNT = 3;
    private static final int THREADS = 8;
    private static final Long USER_ID = 100L;

    @Autowired
    private ReservationCancelService cancelService;

    @Autowired
    private RoundRepository rounds;

    @Autowired
    private ReservationRepository reservations;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private TicketClient ticketClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long reservationId;
    private Long roundId;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        rounds.deleteAll();

        Instant now = Instant.now();

        // reserve() 는 @Modifying 이라 Transaction 을 요구한다. 클래스에 @Transactional 을
        // 걸면 다른 Thread 가 아직 커밋 안 된 데이터를 못 봐서 동시성 검증이 성립하지 않으므로,
        // 준비 구간만 감싸서 커밋시킨다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Round round = Round.create(1L, now.plusSeconds(864000), now.plusSeconds(867600),
                    CAPACITY, 0, now);
            roundId = rounds.save(round).getId();
            rounds.reserve(roundId, HEADCOUNT);

            Reservation reservation = Reservation.create("R-CNC-0001", roundId, 1L, USER_ID,
                    "홍길동", "01012345678", HEADCOUNT, 0, now);
            reservation.confirm(now);
            reservationId = reservations.save(reservation).getId();
        });
    }

    @Test
    @DisplayName("같은 예약에 동시 취소가 몰려도 정원은 정확히 1회만 반환된다")
    void releasesCapacityOnlyOnce() throws Exception {
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    cancelService.cancel(reservationId, new AuthenticatedUser(USER_ID, "USER"));
                    ok.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        // 멱등이므로 실패가 없어야 하지만, 중요한 것은 아래 두 줄이다.
        assertThat(ok.get() + failed.get()).isEqualTo(THREADS);

        Round round = rounds.findById(roundId).orElseThrow();
        assertThat(round.remaining()).isEqualTo(CAPACITY);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }
}
