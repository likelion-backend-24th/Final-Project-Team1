package com.team1.reservation.reservation;

import com.team1.reservation.client.ExpoClient;
import com.team1.payment.PaymentService;
import com.team1.payment.PaymentTransaction;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationService;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.support.IntegrationTestSupport;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * #76 의 핵심 검증. 정원 10 인 회차에 20건이 동시에 몰려도 정확히 10건만 성공해야 한다.
 */
class ConcurrentReservationTest extends IntegrationTestSupport {

    private static final int CAPACITY = 10;
    private static final int THREADS = 20;
    private static final int FEE = 10000;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RoundRepository rounds;

    @Autowired
    private ReservationRepository reservations;

    @MockitoBean
    private ExpoClient expoClient;

    /*
     * 결제 모듈은 실제 PortOne 을 부르므로 Test 에서는 대체한다. 여기서 검증하려는 것은
     * 정원 차감과 중복 판정이지 결제가 아니다.
     */
    @MockitoBean
    private PaymentService paymentService;

    private Long roundId;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        rounds.deleteAll();

        Instant now = Instant.now();
        Round round = Round.create(1L, now.plusSeconds(86400), now.plusSeconds(90000), CAPACITY, FEE, now);
        roundId = rounds.save(round).getId();

        when(expoClient.getExpo(anyLong())).thenReturn(new ExpoSummary(1L, 99L, "PUBLISHED"));
        when(paymentService.createPending(any(), any())).thenAnswer(call ->
                PaymentTransaction.create(call.getArgument(0), "BE24-01-01JABCDEF",
                        call.getArgument(1), Instant.now()));
    }

    @Test
    @DisplayName("정원 10에 동시 20건이 들어와도 정확히 10건만 성공하고 정원을 넘지 않는다")
    void doesNotExceedCapacityUnderConcurrency() throws Exception {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            long userId = i + 1L;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reservationService.create(roundId,
                            new AuthenticatedUser(userId, "USER"),
                            new CreateReservationRequest(1, "예약자" + userId, "01012345678"));
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(success.get()).isEqualTo(CAPACITY);
        assertThat(failure.get()).isEqualTo(THREADS - CAPACITY);

        Round after = rounds.findById(roundId).orElseThrow();
        assertThat(after.getReservedCount()).isEqualTo(CAPACITY);
        assertThat(after.remaining()).isZero();

        List<Reservation> saved = reservations.findAll();
        assertThat(saved).hasSize(CAPACITY);
        assertThat(saved).allSatisfy(r -> assertThat(r.getAmount()).isEqualTo(FEE));
    }

    @Test
    @DisplayName("정원을 초과하는 요청은 예약 행을 남기지 않는다 - 부분 예약도 만들지 않는다")
    void leavesNoRowWhenCapacityExceeded() {
        reservationService.create(roundId, new AuthenticatedUser(1L, "USER"),
                new CreateReservationRequest(CAPACITY, "예약자", "01012345678"));

        assertThat(reservations.findAll()).hasSize(1);

        try {
            reservationService.create(roundId, new AuthenticatedUser(2L, "USER"),
                    new CreateReservationRequest(1, "예약자2", "01012345679"));
        } catch (Exception ignored) {
            // 정원 초과로 거절되는 것이 정상이다
        }

        assertThat(reservations.findAll()).hasSize(1);
        assertThat(rounds.findById(roundId).orElseThrow().getReservedCount()).isEqualTo(CAPACITY);
    }
}
