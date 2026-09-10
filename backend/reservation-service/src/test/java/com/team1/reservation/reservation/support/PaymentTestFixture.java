package com.team1.reservation.reservation.support;

import com.team1.payment.PaymentService;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationPaymentService;
import com.team1.reservation.reservation.service.TicketIssueNotifier;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #78 Test 들이 공유하는 고정값과 Mock 조립. 같은 Fixture 를 다섯 클래스가 각자 만들면
 * 금액·인원 같은 값이 어긋나서 Test 끼리 다른 전제를 갖게 된다.
 */
public abstract class PaymentTestFixture {

    protected static final Instant NOW = Instant.parse("2026-09-08T04:00:00Z");
    protected static final Long RESERVATION_ID = 42L;
    protected static final Long ROUND_ID = 7L;
    protected static final Long EXPO_ID = 1L;
    protected static final Long USER_ID = 100L;
    protected static final int HEADCOUNT = 2;
    protected static final int AMOUNT = 20000;

    protected static final AuthenticatedUser MEMBER = new AuthenticatedUser(USER_ID, "USER");

    protected ReservationRepository reservations;
    protected RoundRepository rounds;
    protected PaymentService paymentService;
    protected TicketClient ticketClient;
    protected ReservationPaymentService service;

    protected void initMocks() {
        reservations = mock(ReservationRepository.class);
        rounds = mock(RoundRepository.class);
        paymentService = mock(PaymentService.class);
        ticketClient = mock(TicketClient.class);
        // Transaction 이 없으므로 AfterCommitExecutor 는 통지를 그 자리에서 실행한다.
        TicketIssueNotifier notifier = TicketDispatchStub.notifier(ticketClient, Clock.fixed(NOW, ZoneOffset.UTC));
        service = new ReservationPaymentService(reservations, rounds, paymentService, notifier,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    protected Reservation pending() {
        return Reservation.create("R-4K7Q-W2M8", ROUND_ID, EXPO_ID, USER_ID,
                "홍길동", "01012345678", HEADCOUNT, AMOUNT, NOW);
    }

    protected Reservation given(Reservation reservation) {
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        return reservation;
    }
}
