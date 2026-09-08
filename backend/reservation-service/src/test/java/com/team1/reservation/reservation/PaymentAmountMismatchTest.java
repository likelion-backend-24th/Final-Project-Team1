package com.team1.reservation.reservation;

import com.team1.payment.PaymentApprovalResult;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.support.PaymentTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 금액 불일치는 모듈이 AMOUNT_MISMATCH 로 알려준다. PG 는 PAID 라고 했으니 실패로 기록할 수 없고,
 * 금액이 안 맞으니 확정할 수도 없다 - 양쪽 다 하지 않고 사람이 확인할 여지를 남기는 것이 계약이다.
 */
class PaymentAmountMismatchTest extends PaymentTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.amountMismatch());
    }

    @Test
    @DisplayName("모듈이 금액 불일치를 알리면 400 이고 확정하지 않는다")
    void rejectsMismatchedAmount() {
        Reservation reservation = given(pending());

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("금액 불일치는 취소가 아니다 - 정원을 반환하지 않는다")
    void doesNotCancelOnMismatch() {
        Reservation reservation = given(pending());

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER))
                .isInstanceOf(ApiException.class);

        assertThat(reservation.getCancelledAt()).isNull();
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("PENDING 으로 남으므로 원인을 고친 뒤 재시도할 수 있다")
    void staysRetryable() {
        Reservation reservation = given(pending());

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER))
                .isInstanceOf(ApiException.class);

        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.success(AMOUNT));

        assertThat(service.confirm(RESERVATION_ID, MEMBER).getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(NOW);
    }
}
