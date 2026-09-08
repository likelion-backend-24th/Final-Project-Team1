package com.team1.reservation.reservation;

import com.team1.payment.PgInquiryResult;
import com.team1.payment.PgPaymentStatus;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAmountMismatchTest extends PaymentTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    private void givenPaidAmount(Integer amount) {
        when(pgClient.inquire(PAYMENT_ID)).thenReturn(
                new PgInquiryResult(PgPaymentStatus.PAID, amount, "pg-tx-1", "0000", null));
    }

    @Test
    @DisplayName("검증 금액이 예약 금액과 다르면 400 이고 확정하지 않는다")
    void rejectsMismatchedAmount() {
        Reservation reservation = given(pending());
        givenPaidAmount(AMOUNT - 1000);

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("금액 불일치는 취소가 아니다 - 정원을 반환하지 않고 사람이 확인할 여지를 남긴다")
    void doesNotCancelOnMismatch() {
        Reservation reservation = given(pending());
        givenPaidAmount(AMOUNT * 2);

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID))
                .isInstanceOf(ApiException.class);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getCancelledAt()).isNull();
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("금액이 비어 있으면 대조할 수 없으므로 확정하지 않는다")
    void rejectsNullAmount() {
        Reservation reservation = given(pending());
        givenPaidAmount(null);

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
}
