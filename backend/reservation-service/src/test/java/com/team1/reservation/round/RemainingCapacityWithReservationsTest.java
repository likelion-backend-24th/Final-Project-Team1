package com.team1.reservation.round;

import com.team1.payment.PaymentApprovalResult;
import com.team1.payment.PaymentService;
import com.team1.payment.PaymentTransaction;
import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationExpiryWriter;
import com.team1.reservation.reservation.service.ReservationPaymentService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * #81 잔여 정원이 실제 예약을 반영하는지 확인한다.
 *
 * <p>Sprint 1 에는 예약 데이터가 없어 {@code remaining = capacity} 였고, Sprint 2 에
 * {@code capacity - reserved_count} 로 교체했다. 이 Test 가 검증하는 것은
 * <b>결제대기 인원도 빠져 있는가</b> 다 — 결제 중인 좌석을 남은 것으로 보여주면
 * 두 사람이 같은 자리를 잡는다.
 *
 * <p>DB 의 조건부 UPDATE 가 실제로 카운터를 움직이는지 봐야 하므로 통합 Test 다.
 */
class RemainingCapacityWithReservationsTest extends IntegrationTestSupport {

    private static final int CAPACITY = 10;
    private static final int FEE = 10000;
    private static final Long EXPO_ID = 1L;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationPaymentService paymentTransition;

    @Autowired
    private ReservationExpiryWriter expiryWriter;

    @Autowired
    private RoundRepository rounds;

    @Autowired
    private ReservationRepository reservations;

    @MockitoBean
    private ExpoClient expoClient;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private TicketClient ticketClient;

    private Long roundId;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        rounds.deleteAll();

        Instant now = Instant.now();
        roundId = rounds.save(Round.create(EXPO_ID,
                now.plusSeconds(86400), now.plusSeconds(90000), CAPACITY, FEE, now)).getId();

        when(expoClient.getExpo(anyLong())).thenReturn(new ExpoSummary(EXPO_ID, 99L, "PUBLISHED"));
        when(paymentService.createPending(any(), any())).thenAnswer(call ->
                PaymentTransaction.create(call.getArgument(0), "BE24-01-TEST",
                        call.getArgument(1), Instant.now()));
    }

    private int remaining() {
        return rounds.findById(roundId).orElseThrow().remaining();
    }

    /** 예약 1건을 만들고 PENDING 으로 둔다. */
    private Reservation reserve(long userId, int headcount) {
        return reservationService.create(roundId,
                new AuthenticatedUser(userId, "USER"),
                new CreateReservationRequest(headcount, "예약자" + userId, "01012345678")).reservation();
    }

    private Reservation confirm(long userId, int headcount) {
        Reservation reservation = reserve(userId, headcount);
        when(paymentService.confirm(reservation.getId()))
                .thenReturn(PaymentApprovalResult.success(reservation.getAmount()));
        paymentTransition.confirm(reservation.getId(), new AuthenticatedUser(userId, "USER"));
        return reservation;
    }

    @Test
    @DisplayName("예약이 없으면 잔여 정원은 정원과 같다")
    void remainingEqualsCapacityWithoutReservations() {
        assertThat(remaining()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("확정 3명이면 capacity - 3")
    void subtractsConfirmedHeadcount() {
        confirm(1L, 3);

        assertThat(remaining()).isEqualTo(CAPACITY - 3);
    }

    @Test
    @DisplayName("결제대기 2명이 더 있으면 capacity - 5 - 결제 중인 좌석도 빠져야 한다")
    void subtractsPendingHeadcountToo() {
        confirm(1L, 3);
        reserve(2L, 2);

        assertThat(remaining()).isEqualTo(CAPACITY - 5);
    }

    @Test
    @DisplayName("결제대기가 만료되면 그 인원만 돌아온다 - capacity - 3 으로 복귀")
    void restoresOnlyExpiredHeadcount() {
        confirm(1L, 3);
        Reservation pending = reserve(2L, 2);
        assertThat(remaining()).isEqualTo(CAPACITY - 5);

        expiryWriter.expire(pending.getId(), roundId, pending.getHeadcount());

        assertThat(remaining()).isEqualTo(CAPACITY - 3);
    }

    @Test
    @DisplayName("확정은 정원을 다시 차감하지 않는다 - 차감은 예약을 만들 때 이미 끝났다")
    void confirmDoesNotSubtractAgain() {
        Reservation reservation = reserve(1L, 4);
        assertThat(remaining()).isEqualTo(CAPACITY - 4);

        when(paymentService.confirm(reservation.getId()))
                .thenReturn(PaymentApprovalResult.success(reservation.getAmount()));
        paymentTransition.confirm(reservation.getId(), new AuthenticatedUser(1L, "USER"));

        assertThat(remaining()).isEqualTo(CAPACITY - 4);
    }

    @Test
    @DisplayName("정원을 넘기는 예약은 거절되고 잔여 정원도 그대로다")
    void rejectsOverCapacityWithoutChangingRemaining() {
        confirm(1L, 8);
        assertThat(remaining()).isEqualTo(2);

        try {
            reserve(2L, 3);
        } catch (RuntimeException expected) {
            // CAPACITY_EXCEEDED
        }

        assertThat(remaining()).isEqualTo(2);
    }
}
