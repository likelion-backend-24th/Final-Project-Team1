package com.team1.reservation.reservation.support;

import com.team1.payment.PaymentService;
import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;
import com.team1.payment.PaymentTransactionRepository;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationExpiryService;
import com.team1.reservation.reservation.service.ReservationExpiryWriter;
import com.team1.reservation.reservation.service.ReservationPaymentService;
import com.team1.reservation.reservation.service.TicketIssueNotifier;
import com.team1.reservation.round.repository.RoundRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** #77 만료 Test 3종이 공유하는 Mock 조립. 시각은 Clock 으로 고정한다. */
public abstract class ExpiryTestFixture {

    protected static final Instant NOW = Instant.parse("2026-09-09T04:00:00Z");
    protected static final Duration GRACE = Duration.ofMinutes(10);
    protected static final Long ROUND_ID = 7L;
    protected static final Long EXPO_ID = 1L;
    protected static final Long USER_ID = 100L;
    protected static final int HEADCOUNT = 2;
    protected static final int AMOUNT = 20000;

    protected ReservationRepository reservations;
    protected RoundRepository rounds;
    protected PaymentTransactionRepository payments;
    protected PaymentService paymentService;
    protected TicketClient ticketClient;
    protected ReservationExpiryService service;

    protected void initMocks() {
        initMocks(NOW, 200, 50);
    }

    protected void initMocks(Instant now, int batchSize, int pgBatchSize) {
        reservations = mock(ReservationRepository.class);
        rounds = mock(RoundRepository.class);
        payments = mock(PaymentTransactionRepository.class);
        paymentService = mock(PaymentService.class);
        ticketClient = mock(TicketClient.class);

        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        TicketIssueNotifier notifier = TicketDispatchStub.notifier(ticketClient, clock);
        ReservationPaymentService transition =
                new ReservationPaymentService(reservations, rounds, paymentService, notifier, clock);
        ReservationExpiryWriter writer = new ReservationExpiryWriter(reservations, rounds, transition);

        service = new ReservationExpiryService(reservations, payments, paymentService, writer,
                clock, GRACE, batchSize, pgBatchSize);
    }

    /** 만료 시각을 한참 지난 PENDING 예약(expiresAt = NOW-20분). 유예 10분도 넘긴 상태다. */
    protected Reservation expired(Long id) {
        return build(id, Duration.ofMinutes(30));
    }

    /** 막 만료된 예약(expiresAt = NOW-2분). 유예 10분 안이다. */
    protected Reservation justExpired(Long id) {
        return build(id, Duration.ofMinutes(12));
    }

    private Reservation build(Long id, Duration age) {
        Reservation reservation = Reservation.create("R-4K7Q-W2M" + id, ROUND_ID, EXPO_ID, USER_ID,
                "홍길동", "01012345678", HEADCOUNT, AMOUNT, NOW.minus(age));
        ReflectionTestUtils.setField(reservation, "id", id);
        when(reservations.findById(id)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    protected void givenCandidates(Reservation... candidates) {
        when(reservations.findExpirable(any(), any())).thenReturn(List.of(candidates));
    }

    /** 결제를 시도한 적이 없다 — payment_transactions 에 행이 없다. */
    protected void givenNoPayment(Long reservationId) {
        when(payments.findByRefId(reservationId)).thenReturn(Optional.empty());
    }

    protected void givenPayment(Long reservationId, PaymentStatus status) {
        PaymentTransaction payment =
                PaymentTransaction.create(reservationId, "BE24-01-TEST" + reservationId, AMOUNT, NOW);
        ReflectionTestUtils.setField(payment, "status", status);
        when(payments.findByRefId(reservationId)).thenReturn(Optional.of(payment));
    }
}
