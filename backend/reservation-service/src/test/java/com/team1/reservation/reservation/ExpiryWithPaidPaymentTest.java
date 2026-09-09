package com.team1.reservation.reservation;

import com.team1.payment.PaymentApprovalResult;
import com.team1.payment.PaymentStatus;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.support.ExpiryTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #77 의 존재 이유. 시간이 지났다고 무조건 만료시키면, 마지막 순간에 결제하고 웹훅이
 * 유실된 사용자의 돈을 받은 채로 자리를 뺏는다.
 */
class ExpiryWithPaidPaymentTest extends ExpiryTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    @Test
    @DisplayName("PG 가 PAID 면 만료시키지 않고 확정한다. 정원은 반환하지 않는다")
    void confirmsInsteadOfExpiringWhenPaid() {
        Reservation reservation = expired(1L);
        givenCandidates(reservation);
        givenPayment(1L, PaymentStatus.PENDING);
        when(paymentService.confirm(1L)).thenReturn(PaymentApprovalResult.success(AMOUNT));

        assertThat(service.expireDue()).isEqualTo(1);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(NOW);
        verify(reservations, never()).expireIfPending(anyLong());
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("확정 경로이므로 티켓 발급도 통지한다")
    void notifiesTicketIssueOnConfirm() {
        givenCandidates(expired(1L));
        givenPayment(1L, PaymentStatus.PENDING);
        when(paymentService.confirm(1L)).thenReturn(PaymentApprovalResult.success(AMOUNT));

        service.expireDue();

        verify(ticketClient, times(1)).issueTicket(any());
    }

    @Test
    @DisplayName("PG 가 실패를 확정하면 EXPIRED 가 아니라 CANCELLED 다 - 시간이 아니라 결제가 실패한 것이다")
    void cancelsInsteadOfExpiringWhenPaymentFailed() {
        Reservation reservation = expired(1L);
        givenCandidates(reservation);
        givenPayment(1L, PaymentStatus.PENDING);
        when(paymentService.confirm(1L)).thenReturn(PaymentApprovalResult.failedConfirmed("카드 한도 초과"));

        assertThat(service.expireDue()).isEqualTo(1);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(rounds, times(1)).release(ROUND_ID, HEADCOUNT);
        verify(ticketClient, never()).issueTicket(any());
    }

    @Test
    @DisplayName("이미 확정된 예약은 건드리지 않는다 - 웹훅이 먼저 이긴 경우")
    void skipsAlreadyConfirmed() {
        Reservation reservation = expired(1L);
        reservation.confirm(NOW);
        givenCandidates(reservation);
        givenPayment(1L, PaymentStatus.PENDING);
        when(paymentService.confirm(1L)).thenReturn(PaymentApprovalResult.success(AMOUNT));

        assertThat(service.expireDue()).isZero();

        verify(rounds, never()).release(anyLong(), anyInt());
        verify(ticketClient, never()).issueTicket(any());
    }
}
