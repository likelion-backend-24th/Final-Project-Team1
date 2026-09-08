package com.team1.reservation.reservation;

import com.team1.payment.PgInquiryResult;
import com.team1.payment.PgPaymentStatus;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.support.PaymentTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 실패확정 시 정원이 정확히 1회 반환되는지 본다. 두 번 반환되면 팔지도 않은 자리가 늘어난다.
 */
class PaymentFailureRollbackTest extends PaymentTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
        when(pgClient.inquire(PAYMENT_ID)).thenReturn(
                new PgInquiryResult(PgPaymentStatus.FAILED, null, null, "F001", "카드 한도 초과"));
    }

    @Test
    @DisplayName("실패확정이면 CANCELLED 로 전이하고 정원을 예약 인원만큼 되돌린다")
    void cancelsAndReleasesCapacity() {
        Reservation reservation = given(pending());

        Reservation result = service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(result.getCancelledAt()).isEqualTo(NOW);
        assertThat(reservation.getConfirmedAt()).isNull();

        verify(rounds).release(ROUND_ID, HEADCOUNT);
    }

    @Test
    @DisplayName("실패 응답이 두 번 와도 정원 반환은 1회뿐이다 - 두 번째는 409 로 막힌다")
    void releasesCapacityExactlyOnce() {
        given(pending());

        service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID);

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID))
                .hasMessageContaining("already");

        verify(rounds, times(1)).release(ROUND_ID, HEADCOUNT);
    }
}
