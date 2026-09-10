package com.team1.reservation.reservation;

import com.team1.payment.PaymentService;
import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;
import com.team1.payment.PaymentTransactionRepository;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.CancelReservationResponse;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationCancelService;
import com.team1.reservation.reservation.support.TicketDispatchStub;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 취소·환불 규칙. 취소는 회차 시작 전까지, 전액 환불은 시작 24시간 전까지.
 * 그 사이에 취소하면 취소는 되지만 환불은 없다.
 */
class ReservationCancelServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T04:00:00Z");
    private static final Long RESERVATION_ID = 42L;
    private static final Long ROUND_ID = 7L;
    private static final Long EXPO_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final int HEADCOUNT = 2;
    private static final int AMOUNT = 20000;
    private static final AuthenticatedUser MEMBER = new AuthenticatedUser(USER_ID, "USER");

    private ReservationRepository reservations;
    private RoundRepository rounds;
    private PaymentTransactionRepository payments;
    private PaymentService paymentService;
    private TicketClient ticketClient;
    private ReservationCancelService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        rounds = mock(RoundRepository.class);
        payments = mock(PaymentTransactionRepository.class);
        paymentService = mock(PaymentService.class);
        ticketClient = mock(TicketClient.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ReservationCancelService(reservations, rounds, payments, paymentService,
                TicketDispatchStub.notifier(ticketClient, clock), clock);

        when(reservations.cancelIfActive(anyLong(), any())).thenReturn(1);
    }

    /** 회차 시작까지 남은 시간을 정해 상황을 만든다. */
    private void givenRoundStartingIn(Duration untilStart, int fee) {
        Instant startsAt = NOW.plus(untilStart);
        Round round = Round.create(EXPO_ID, startsAt, startsAt.plusSeconds(7200), 100, fee,
                NOW.minus(Duration.ofDays(30)));
        when(rounds.findById(ROUND_ID)).thenReturn(Optional.of(round));

        Reservation reservation = Reservation.create("R-4K7Q-W2M8", ROUND_ID, EXPO_ID, USER_ID,
                "홍길동", "01012345678", HEADCOUNT, fee * HEADCOUNT, NOW.minus(Duration.ofDays(3)));
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);
        reservation.confirm(NOW.minus(Duration.ofDays(3)));
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
    }

    private void givenPaidPayment() {
        PaymentTransaction payment =
                PaymentTransaction.create(RESERVATION_ID, "BE24-01-TEST", AMOUNT, NOW);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
        when(payments.findByRefId(RESERVATION_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    @DisplayName("회차 시작 24시간 전보다 이르면 전액 환불한다")
    void refundsInFullBeforeWindow() {
        givenRoundStartingIn(Duration.ofDays(3), 10000);
        givenPaidPayment();

        CancelReservationResponse response = service.cancel(RESERVATION_ID, MEMBER);

        assertThat(response.refunded()).isTrue();
        assertThat(response.refundAmount()).isEqualTo(20000);
        verify(paymentService).cancel(RESERVATION_ID, "user cancellation");
    }

    @Test
    @DisplayName("24시간 안으로 들어오면 취소는 되지만 환불은 없다")
    void cancelsWithoutRefundInsideWindow() {
        givenRoundStartingIn(Duration.ofHours(5), 10000);
        givenPaidPayment();

        CancelReservationResponse response = service.cancel(RESERVATION_ID, MEMBER);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.refunded()).isFalse();
        assertThat(response.refundAmount()).isZero();
        verify(paymentService, never()).cancel(anyLong(), anyString());
    }

    @Test
    @DisplayName("경계는 정확히 24시간 - 24시간 0초 전은 아직 환불된다")
    void refundsExactlyAtBoundary() {
        givenRoundStartingIn(Duration.ofDays(1), 10000);
        givenPaidPayment();

        assertThat(service.cancel(RESERVATION_ID, MEMBER).refunded()).isTrue();
    }

    @Test
    @DisplayName("회차가 시작된 뒤에는 취소 자체가 안 된다 - 정원도 건드리지 않는다")
    void rejectsAfterRoundStart() {
        givenRoundStartingIn(Duration.ofHours(-1), 10000);

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, MEMBER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        verify(reservations, never()).cancelIfActive(anyLong(), any());
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("전이에 성공한 경우에만 정원을 돌려준다")
    void releasesCapacityOnlyOnTransition() {
        givenRoundStartingIn(Duration.ofDays(3), 10000);
        givenPaidPayment();

        service.cancel(RESERVATION_ID, MEMBER);
        verify(rounds).release(ROUND_ID, HEADCOUNT);
    }

    @Test
    @DisplayName("만료 배치가 먼저 끝냈으면 409 이고 정원을 두 번 반환하지 않는다")
    void rejectsWhenAlreadyFinalised() {
        givenRoundStartingIn(Duration.ofDays(3), 10000);
        when(reservations.cancelIfActive(anyLong(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, MEMBER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        verify(rounds, never()).release(anyLong(), anyInt());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("취소하면 티켓 무효화를 통지한다")
    void notifiesTicketRevoke() {
        givenRoundStartingIn(Duration.ofDays(3), 10000);
        givenPaidPayment();

        service.cancel(RESERVATION_ID, MEMBER);

        verify(ticketClient).revokeTicket(RESERVATION_ID);
    }

    @Test
    @DisplayName("무료 회차는 환불 단계를 건너뛴다")
    void skipsRefundForFreeRound() {
        givenRoundStartingIn(Duration.ofDays(3), 0);

        assertThat(service.cancel(RESERVATION_ID, MEMBER).refunded()).isFalse();
        verifyNoInteractions(paymentService);
        verify(ticketClient).revokeTicket(RESERVATION_ID);
    }

    @Test
    @DisplayName("결제가 PAID 가 아니면 환불하지 않는다 - 돌려줄 돈이 없다")
    void skipsRefundWhenPaymentNotPaid() {
        givenRoundStartingIn(Duration.ofDays(3), 10000);
        PaymentTransaction payment =
                PaymentTransaction.create(RESERVATION_ID, "BE24-01-TEST", AMOUNT, NOW);
        when(payments.findByRefId(RESERVATION_ID)).thenReturn(Optional.of(payment));

        assertThat(service.cancel(RESERVATION_ID, MEMBER).refunded()).isFalse();
        verify(paymentService, never()).cancel(anyLong(), anyString());
    }

    @Test
    @DisplayName("남의 예약이면 403 이고 아무것도 건드리지 않는다")
    void rejectsOtherMembersReservation() {
        givenRoundStartingIn(Duration.ofDays(3), 10000);

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, new AuthenticatedUser(999L, "USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(reservations, never()).cancelIfActive(anyLong(), any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("USER 가 아닌 Role 은 403, 미인증은 401")
    void rejectsNonMember() {
        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, new AuthenticatedUser(1L, "ORGANIZER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));

        verify(reservations, never()).findById(anyLong());
    }
}
