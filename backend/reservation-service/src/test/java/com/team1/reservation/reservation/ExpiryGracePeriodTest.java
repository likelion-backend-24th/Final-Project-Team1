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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #77 유예 상한. 무한 보류는 PG 무응답이 이어질 때 정원을 영구히 묶으므로,
 * 결제대기 창과 같은 길이(10분)를 한 번 더 주고 그래도 모르면 만료시킨다.
 */
class ExpiryGracePeriodTest extends ExpiryTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    private void givenUnknown(Reservation reservation) {
        givenCandidates(reservation);
        givenPayment(reservation.getId(), PaymentStatus.PENDING);
        when(paymentService.confirm(reservation.getId()))
                .thenReturn(PaymentApprovalResult.unknown("PG 무응답"));
    }

    @Test
    @DisplayName("유예 안에서 모른다고 하면 보류한다 - 만료도 정원 반환도 하지 않는다")
    void holdsWhileWithinGrace() {
        // expiresAt = NOW-2분, 유예 상한 = NOW+8분. 아직 안 지났다.
        Reservation reservation = justExpired(1L);
        givenUnknown(reservation);

        assertThat(service.expireDue()).isZero();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(reservations, never()).expireIfPending(anyLong());
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("유예 상한을 넘기면 만료시키고 정원을 반환한다")
    void expiresAfterGraceElapsed() {
        // expiresAt = NOW-20분, 유예 상한 = NOW-10분. 이미 지났다.
        givenUnknown(expired(1L));
        when(reservations.expireIfPending(1L)).thenReturn(1);

        assertThat(service.expireDue()).isEqualTo(1);
        verify(rounds).release(ROUND_ID, HEADCOUNT);
    }

    @Test
    @DisplayName("금액 불일치도 보류 대상이다 - 자동으로 취소하지 않는다")
    void holdsOnAmountMismatch() {
        Reservation reservation = justExpired(1L);
        givenCandidates(reservation);
        givenPayment(1L, PaymentStatus.PENDING);
        when(paymentService.confirm(1L)).thenReturn(PaymentApprovalResult.amountMismatch());

        assertThat(service.expireDue()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("유예를 넘겨도 전이에 실패하면 정원을 반환하지 않는다")
    void doesNotReleaseWhenTransitionLost() {
        givenUnknown(expired(1L));
        when(reservations.expireIfPending(1L)).thenReturn(0);

        assertThat(service.expireDue()).isZero();
        verify(rounds, never()).release(anyLong(), anyInt());
    }
}
