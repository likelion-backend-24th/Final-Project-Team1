package com.team1.reservation.round;

import com.team1.payment.PaymentApprovalResult;
import com.team1.payment.PaymentService;
import com.team1.payment.PaymentTransaction;
import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationCancelService;
import com.team1.reservation.reservation.service.ReservationPaymentService;
import com.team1.reservation.reservation.service.ReservationService;
import com.team1.reservation.round.dto.UpdateRoundRequest;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.round.service.RoundService;
import com.team1.reservation.support.IntegrationTestSupport;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * S9-2 회차 수정. 조건부 UPDATE 가 실제 DB 에서 활성 예약을 막는지 봐야 하므로 통합 Test 다.
 *
 * <p>핵심은 마지막 두 개다 - 예약을 취소하면 다시 수정할 수 있어야 하고,
 * 참가비를 바꿔도 기존 예약의 결제 금액은 그대로여야 한다.
 *
 * <p>시각 경계와 소유권·검증은 시계를 고정할 수 있는 {@code UpdateRoundValidationTest} 가 본다.
 */
class UpdateRoundServiceTest extends IntegrationTestSupport {

    private static final int CAPACITY = 10;
    private static final int FEE = 10000;
    private static final Long EXPO_ID = 1L;
    private static final long OWNER_ID = 99L;

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(OWNER_ID, "ORGANIZER");

    @Autowired
    private RoundService roundService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationPaymentService paymentTransition;

    @Autowired
    private ReservationCancelService cancelService;

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
    private Instant now;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        rounds.deleteAll();

        // MySQL DATETIME(6) 은 마이크로초까지다. 나노초를 그대로 쓰면 저장 시 반올림되어
        // 돌려받은 값이 보낸 값과 달라진다(Windows 의 Instant.now() 는 나노초를 준다).
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        roundId = rounds.save(Round.create(EXPO_ID,
                now.plusSeconds(86400), now.plusSeconds(90000), CAPACITY, FEE, now)).getId();

        when(expoClient.getExpo(anyLong())).thenReturn(new ExpoSummary(EXPO_ID, OWNER_ID, "PUBLISHED"));
        when(paymentService.createPending(any(), any())).thenAnswer(call ->
                PaymentTransaction.create(call.getArgument(0), "BE24-01-TEST",
                        call.getArgument(1), Instant.now()));
    }

    private UpdateRoundRequest request(int capacity, int fee) {
        return new UpdateRoundRequest(now.plusSeconds(172800), now.plusSeconds(180000), capacity, fee);
    }

    private Reservation confirm(long userId, int headcount) {
        Reservation reservation = reservationService.create(roundId,
                new AuthenticatedUser(userId, "USER"),
                new CreateReservationRequest(headcount, "예약자" + userId, "01012345678")).reservation();
        when(paymentService.confirm(reservation.getId()))
                .thenReturn(PaymentApprovalResult.success(reservation.getAmount()));
        paymentTransition.confirm(reservation.getId(), new AuthenticatedUser(userId, "USER"));
        return reservation;
    }

    @Test
    @DisplayName("예약이 없으면 일정·정원·참가비가 바뀐다")
    void updatesWhenNoReservation() {
        Round updated = roundService.update(EXPO_ID, roundId, OWNER, request(50, 20000));

        assertThat(updated.getCapacity()).isEqualTo(50);
        assertThat(updated.getFee()).isEqualTo(20000);
        assertThat(updated.getStartsAt()).isEqualTo(now.plusSeconds(172800));
    }

    @Test
    @DisplayName("활성 예약이 1건이라도 있으면 409 이고 값이 바뀌지 않는다")
    void rejectsWhenReservationExists() {
        confirm(1L, 2);

        assertThatThrownBy(() -> roundService.update(EXPO_ID, roundId, OWNER, request(50, 20000)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ROUND_HAS_RESERVATIONS));

        Round unchanged = rounds.findById(roundId).orElseThrow();
        assertThat(unchanged.getCapacity()).isEqualTo(CAPACITY);
        assertThat(unchanged.getFee()).isEqualTo(FEE);
    }

    @Test
    @DisplayName("예약을 모두 취소하면 다시 수정할 수 있다 - reserved_count 가 0 으로 돌아온다")
    void allowsUpdateAfterAllReservationsCancelled() {
        Reservation reservation = confirm(1L, 2);
        cancelService.cancel(reservation.getId(), new AuthenticatedUser(1L, "USER"));

        Round updated = roundService.update(EXPO_ID, roundId, OWNER, request(50, 20000));

        assertThat(updated.getCapacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("참가비를 바꿔도 기존 예약의 결제 금액은 그대로다 - 금액은 신청 시점에 고정된다")
    void feeChangeDoesNotTouchExistingReservationAmount() {
        Reservation reservation = confirm(1L, 2);
        int amountAtBooking = reservation.getAmount();
        cancelService.cancel(reservation.getId(), new AuthenticatedUser(1L, "USER"));

        roundService.update(EXPO_ID, roundId, OWNER, request(CAPACITY, 99000));

        assertThat(reservations.findById(reservation.getId()).orElseThrow().getAmount())
                .isEqualTo(amountAtBooking);
    }

}
