package com.team1.reservation.reservation;

import com.team1.payment.PaymentStatus;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** #77 갈래 판정과 배치 상한. 정원 반환이 전이 1회당 정확히 1회인지가 핵심이다. */
class ReservationExpirySchedulerTest extends ExpiryTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    @Test
    @DisplayName("갈래 1 - 결제를 시도한 적이 없으면 PG 를 조회하지 않고 바로 만료시킨다")
    void expiresWithoutPgWhenNoPaymentAttempt() {
        givenCandidates(expired(1L));
        givenNoPayment(1L);
        when(reservations.expireIfPending(1L)).thenReturn(1);

        assertThat(service.expireDue()).isEqualTo(1);

        verifyNoInteractions(paymentService);
        verify(rounds).release(ROUND_ID, HEADCOUNT);
    }

    @Test
    @DisplayName("결제가 이미 실패로 확정된 건도 PG 를 다시 조회하지 않는다")
    void expiresWithoutPgWhenPaymentAlreadyFailed() {
        givenCandidates(expired(1L));
        givenPayment(1L, PaymentStatus.FAILED);
        when(reservations.expireIfPending(1L)).thenReturn(1);

        assertThat(service.expireDue()).isEqualTo(1);

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("확정이 먼저 이기면 정원을 돌려주지 않는다 - 오버부킹을 막는 지점이다")
    void doesNotReleaseWhenTransitionLost() {
        givenCandidates(expired(1L));
        givenNoPayment(1L);
        // 조회와 UPDATE 사이에 확정돼서 조건부 UPDATE 가 0 행을 바꾼 상황이다.
        when(reservations.expireIfPending(1L)).thenReturn(0);

        assertThat(service.expireDue()).isZero();
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("PG 조회가 필요한 갈래에는 별도 상한이 걸린다")
    void capsPgLookupsSeparately() {
        initMocks(NOW, 200, 1);
        givenCandidates(expired(1L), expired(2L), expired(3L));
        givenPayment(1L, PaymentStatus.PENDING);
        givenPayment(2L, PaymentStatus.PENDING);
        givenPayment(3L, PaymentStatus.PENDING);
        when(paymentService.confirm(any())).thenReturn(com.team1.payment.PaymentApprovalResult.unknown("PG 무응답"));

        service.expireDue();

        verify(paymentService, times(1)).confirm(any());
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지는 계속 처리한다")
    void keepsGoingAfterOneFailure() {
        givenCandidates(expired(1L), expired(2L), expired(3L));
        givenNoPayment(1L);
        givenNoPayment(2L);
        givenNoPayment(3L);
        when(reservations.expireIfPending(1L)).thenReturn(1);
        when(reservations.expireIfPending(2L)).thenThrow(new RuntimeException("db down"));
        when(reservations.expireIfPending(3L)).thenReturn(1);

        assertThat(service.expireDue()).isEqualTo(2);
    }
}
