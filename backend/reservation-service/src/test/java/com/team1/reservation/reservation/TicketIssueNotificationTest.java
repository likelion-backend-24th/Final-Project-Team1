package com.team1.reservation.reservation;

import com.team1.payment.PaymentApprovalResult;
import com.team1.reservation.client.IssueTicketCommand;
import com.team1.reservation.client.IssuedTicket;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.support.PaymentTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** #79 티켓 발급 통지. 확정에 성공한 경로에서만 나가고, 실패해도 확정을 되돌리지 않는다. */
class TicketIssueNotificationTest extends PaymentTestFixture {

    private static final IssuedTicket TICKET =
            new IssuedTicket(900L, "chk-abc", Instant.parse("2026-09-08T04:00:01Z"));

    @BeforeEach
    void setUp() {
        initMocks();
    }

    private void givenSuccess() {
        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.success(AMOUNT));
    }

    @Test
    @DisplayName("확정되면 예약 정보를 그대로 담아 Ticket-Service 에 통지한다")
    void notifiesOnConfirm() {
        Reservation reservation = given(pending());
        givenSuccess();
        when(ticketClient.issueTicket(any())).thenReturn(TICKET);

        service.confirm(RESERVATION_ID, MEMBER);

        ArgumentCaptor<IssueTicketCommand> captor = ArgumentCaptor.forClass(IssueTicketCommand.class);
        verify(ticketClient).issueTicket(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new IssueTicketCommand(
                reservation.getId(), EXPO_ID, ROUND_ID, USER_ID, HEADCOUNT));
    }

    @Test
    @DisplayName("Ticket-Service 가 죽어 있어도 예약은 CONFIRMED 로 남는다 (fail-open)")
    void keepsConfirmedWhenTicketServiceIsDown() {
        given(pending());
        givenSuccess();
        when(ticketClient.issueTicket(any()))
                .thenThrow(new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "ticket-service unavailable"));

        Reservation confirmed = service.confirm(RESERVATION_ID, MEMBER);

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("통지에서 어떤 예외가 나든 호출자에게 전파되지 않는다")
    void swallowsAnyNotificationFailure() {
        given(pending());
        givenSuccess();
        when(ticketClient.issueTicket(any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> service.confirm(RESERVATION_ID, MEMBER)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 CONFIRMED 인 예약을 다시 확정하면 통지하지 않는다 - 전이가 없었기 때문이다")
    void doesNotNotifyOnIdempotentReplay() {
        Reservation reservation = pending();
        reservation.confirm(NOW);
        given(reservation);

        service.confirm(RESERVATION_ID, MEMBER);

        verifyNoInteractions(ticketClient);
    }

    @Test
    @DisplayName("결제가 실패해 취소되면 통지하지 않는다")
    void doesNotNotifyOnPaymentFailure() {
        given(pending());
        when(paymentService.confirm(any()))
                .thenReturn(PaymentApprovalResult.failedConfirmed("CARD_DECLINED"));

        service.confirm(RESERVATION_ID, MEMBER);

        verifyNoInteractions(ticketClient);
    }

    @Test
    @DisplayName("결제 결과를 모르면 통지하지 않는다 (fail-closed)")
    void doesNotNotifyOnUnknown() {
        given(pending());
        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.unknown("timeout"));

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(ticketClient);
    }
}
