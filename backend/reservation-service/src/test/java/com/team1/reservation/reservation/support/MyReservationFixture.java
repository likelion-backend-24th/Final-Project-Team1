package com.team1.reservation.reservation.support;

import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.repository.PaymentLookupRepository;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.MyReservationService;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** #82 조회 Test 가 공유하는 Mock 조립. */
public abstract class MyReservationFixture {

    protected static final Instant NOW = Instant.parse("2026-09-10T04:00:00Z");
    protected static final Long RESERVATION_ID = 42L;
    protected static final Long ROUND_ID = 7L;
    protected static final Long EXPO_ID = 1L;
    protected static final Long USER_ID = 100L;
    protected static final int HEADCOUNT = 2;
    protected static final int AMOUNT = 20000;
    protected static final int MAX_REFUND_ATTEMPTS = 6;

    protected static final AuthenticatedUser MEMBER = new AuthenticatedUser(USER_ID, "USER");

    protected ReservationRepository reservations;
    protected RoundRepository rounds;
    protected PaymentLookupRepository payments;
    protected TicketClient ticketClient;
    protected MyReservationService service;

    protected void initMocks() {
        reservations = mock(ReservationRepository.class);
        rounds = mock(RoundRepository.class);
        payments = mock(PaymentLookupRepository.class);
        ticketClient = mock(TicketClient.class);

        service = new MyReservationService(reservations, rounds, payments, ticketClient,
                MAX_REFUND_ATTEMPTS);

        when(payments.findByRefIdIn(any())).thenReturn(List.of());
        when(rounds.findById(ROUND_ID)).thenReturn(Optional.of(round()));
        when(rounds.findAllById(any())).thenReturn(List.of(round()));
    }

    protected Round round() {
        Round round = Round.create(EXPO_ID, NOW.plus(Duration.ofDays(3)),
                NOW.plus(Duration.ofDays(3)).plusSeconds(7200), 100, 10000, NOW.minus(Duration.ofDays(30)));
        ReflectionTestUtils.setField(round, "id", ROUND_ID);
        return round;
    }

    protected Reservation reservation(Long id, Long userId, ReservationStatus status) {
        Reservation reservation = Reservation.create("R-4K7Q-W2M" + id, ROUND_ID, EXPO_ID, userId,
                "홍길동", "01012345678", HEADCOUNT, AMOUNT, NOW.minus(Duration.ofDays(3)));
        ReflectionTestUtils.setField(reservation, "id", id);
        ReflectionTestUtils.setField(reservation, "status", status);
        return reservation;
    }

    protected Reservation givenMine(ReservationStatus status) {
        Reservation reservation = reservation(RESERVATION_ID, USER_ID, status);
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        when(reservations.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(reservation));
        return reservation;
    }

    protected void givenPayment(Long reservationId, PaymentStatus status, int attempts) {
        PaymentTransaction payment =
                PaymentTransaction.create(reservationId, "BE24-01-TEST", AMOUNT, NOW);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "attempts", attempts);
        when(payments.findByRefIdIn(any())).thenReturn(List.of(payment));
        when(payments.findByRefIdIn(Set.of(reservationId))).thenReturn(List.of(payment));
    }
}
