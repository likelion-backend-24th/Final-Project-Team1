package com.team1.reservation.reservation;

import com.team1.payment.PaymentService;
import com.team1.payment.PaymentTransaction;
import com.team1.payment.PgCommunicationException;
import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.client.IssueTicketCommand;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationCreation;
import com.team1.reservation.reservation.service.ReservationNoGenerator;
import com.team1.reservation.reservation.service.ReservationService;
import com.team1.reservation.reservation.support.TicketDispatchStub;
import com.team1.reservation.reservation.service.TicketIssueNotifier;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T04:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-09-10T02:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-09-10T08:00:00Z");
    private static final Long ROUND_ID = 7L;
    private static final Long EXPO_ID = 1L;
    private static final String PAYMENT_ID = "BE24-01-01JABCDEF";
    private static final AuthenticatedUser MEMBER = new AuthenticatedUser(100L, "USER");

    private ReservationRepository reservations;
    private RoundRepository rounds;
    private ExpoClient expoClient;
    private PaymentService paymentService;
    protected TicketClient ticketClient;
    private TicketIssueNotifier notifier;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        rounds = mock(RoundRepository.class);
        expoClient = mock(ExpoClient.class);
        paymentService = mock(PaymentService.class);
        ticketClient = mock(TicketClient.class);
        notifier = TicketDispatchStub.notifier(ticketClient, Clock.fixed(NOW, ZoneOffset.UTC));
        ReservationNoGenerator generator = () -> "R-4K7Q-W2M8";

        service = new ReservationService(reservations, rounds, expoClient, paymentService, notifier,
                generator, Clock.fixed(NOW, ZoneOffset.UTC));

        givenRound(10000);
        when(expoClient.getExpo(anyLong())).thenReturn(new ExpoSummary(EXPO_ID, 99L, "PUBLISHED"));
        when(reservations.existsByRoundIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyCollection()))
                .thenReturn(false);
        when(rounds.reserve(anyLong(), anyInt())).thenReturn(1);
        when(reservations.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentService.createPending(any(), any()))
                .thenAnswer(call -> PaymentTransaction.create(
                        call.getArgument(0), PAYMENT_ID, call.getArgument(1), NOW));
    }

    private void givenRound(int fee) {
        when(rounds.findById(ROUND_ID))
                .thenReturn(Optional.of(Round.create(EXPO_ID, STARTS, ENDS, 100, fee, NOW)));
    }

    private CreateReservationRequest request(int headcount) {
        return new CreateReservationRequest(headcount, "  홍길동 ", "010-1234-5678");
    }

    @Test
    @DisplayName("유료 회차는 PENDING 으로 만들고 결제 사전등록의 paymentId 를 함께 돌려준다")
    void createsPendingReservationWithPaymentId() {
        ReservationCreation created = service.create(ROUND_ID, MEMBER, request(3));
        Reservation reservation = created.reservation();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getAmount()).isEqualTo(30000);
        assertThat(reservation.getHeadcount()).isEqualTo(3);
        assertThat(reservation.getRoundId()).isEqualTo(ROUND_ID);
        assertThat(reservation.getExpoId()).isEqualTo(EXPO_ID);
        assertThat(reservation.getUserId()).isEqualTo(MEMBER.userId());
        assertThat(reservation.getExpiresAt()).isEqualTo(NOW.plus(Reservation.PAYMENT_WINDOW));
        assertThat(created.paymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    @DisplayName("결제 사전등록에는 예약 금액을 그대로 넘긴다")
    void registersPaymentWithReservationAmount() {
        service.create(ROUND_ID, MEMBER, request(2));

        verify(paymentService).createPending(any(), eq(20000));
    }

    @Test
    @DisplayName("연락처는 하이픈을 제거하고 이름은 앞뒤 공백을 지워 저장한다")
    void normalizesContact() {
        Reservation reservation = service.create(ROUND_ID, MEMBER, request(1)).reservation();

        assertThat(reservation.getContactPhone()).isEqualTo("01012345678");
        assertThat(reservation.getContactName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("무료 회차는 결제 없이 바로 CONFIRMED 다 - 결제할 것이 없는데 PENDING 으로 두면 만료로 자리만 잃는다")
    void freeReservationIsConfirmedImmediately() {
        givenRound(0);

        ReservationCreation created = service.create(ROUND_ID, MEMBER, request(4));

        assertThat(created.reservation().getAmount()).isZero();
        assertThat(created.reservation().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(created.reservation().getConfirmedAt()).isEqualTo(NOW);
        assertThat(created.paymentId()).isNull();
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("무료 회차도 티켓 발급을 통지한다 - 무료라서 결제 경로를 안 타도 확정은 확정이다")
    void freeReservationNotifiesTicketIssue() {
        givenRound(0);

        service.create(ROUND_ID, MEMBER, request(4));

        ArgumentCaptor<IssueTicketCommand> captor = ArgumentCaptor.forClass(IssueTicketCommand.class);
        verify(ticketClient).issueTicket(captor.capture());
        assertThat(captor.getValue().headcount()).isEqualTo(4);
        assertThat(captor.getValue().expoId()).isEqualTo(EXPO_ID);
    }

    @Test
    @DisplayName("유료 회차는 생성 시점에 통지하지 않는다 - 아직 PENDING 이라 확정된 것이 없다")
    void paidReservationDoesNotNotifyOnCreate() {
        service.create(ROUND_ID, MEMBER, request(2));

        verifyNoInteractions(ticketClient);
    }

    @Test
    @DisplayName("무료 회차도 정원은 똑같이 차감한다")
    void freeReservationStillConsumesCapacity() {
        givenRound(0);

        service.create(ROUND_ID, MEMBER, request(2));

        verify(rounds).reserve(ROUND_ID, 2);
    }

    @Test
    @DisplayName("PG 무응답이면 503 이고 예약을 만들지 않는다 - 결제 못 하는 예약이 자리를 잡고 있으면 안 된다")
    void failsClosedWhenPaymentRegistrationUnavailable() {
        when(paymentService.createPending(any(), any()))
                .thenThrow(new PgCommunicationException("connect timeout"));

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    @Test
    @DisplayName("정원이 부족하면 409 CAPACITY_EXCEEDED 이고 예약 행도 결제도 만들지 않는다")
    void rejectsWhenCapacityExceeded() {
        when(rounds.reserve(anyLong(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(5)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CAPACITY_EXCEEDED));

        verify(reservations, never()).save(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("중복 예약은 정원을 차감하기 전에 거절한다")
    void rejectsDuplicateBeforeReserving() {
        when(reservations.existsByRoundIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_RESERVATION));

        verify(rounds, never()).reserve(anyLong(), anyInt());
        verify(reservations, never()).save(any());
    }

    @Test
    @DisplayName("중복 검사는 PENDING·CONFIRMED 만 대상으로 한다 - 취소·만료는 재예약을 막지 않는다")
    void duplicateCheckOnlyLooksAtActiveStatuses() {
        ArgumentCaptor<Collection<ReservationStatus>> captor = ArgumentCaptor.forClass(Collection.class);

        service.create(ROUND_ID, MEMBER, request(1));

        verify(reservations).existsByRoundIdAndUserIdAndStatusIn(
                eq(ROUND_ID), eq(MEMBER.userId()), captor.capture());

        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("없는 회차는 404")
    void rejectsUnknownRound() {
        when(rounds.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(999L, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("이미 종료된 회차는 404 - 존재 여부를 흘리지 않는다")
    void rejectsFinishedRound() {
        ReservationService late = new ReservationService(reservations, rounds, expoClient, paymentService,
                notifier, () -> "R-4K7Q-W2M8", Clock.fixed(ENDS.plusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> late.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(rounds, never()).reserve(anyLong(), anyInt());
    }

    @Test
    @DisplayName("마감된 박람회의 회차는 404")
    void rejectsClosedExpo() {
        when(expoClient.getExpo(anyLong())).thenReturn(new ExpoSummary(EXPO_ID, 99L, "CLOSED"));

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("박람회 상태를 확인하지 못하면 예약을 만들지 않는다 - 쓰기는 fail-closed 다")
    void failsClosedWhenExpoServiceUnavailable() {
        when(expoClient.getExpo(anyLong()))
                .thenThrow(new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "expo-service unavailable"));

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));

        verify(rounds, never()).reserve(anyLong(), anyInt());
        verify(reservations, never()).save(any());
    }

    @Test
    @DisplayName("USER 가 아닌 Role 은 403, 미인증은 401")
    void rejectsNonMember() {
        assertThatThrownBy(() -> service.create(ROUND_ID, new AuthenticatedUser(1L, "ORGANIZER"), request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.create(ROUND_ID, null, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
