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
 * "모름" 경로. 결제 여부를 확인하지 못한 상태에서 예약을 확정하면 돈 안 낸 사람에게 자리를 주고,
 * 취소하면 돈 낸 사람의 자리를 뺏는다. 그래서 아무것도 하지 않는 것이 유일하게 안전한 선택이다.
 *
 * <p>PG 무응답과 "거래 없음" 을 모듈이 둘 다 UNKNOWN 으로 합쳐서 주므로 이 Service 는 한 갈래만 본다.
 */
class PaymentUnknownFailClosedTest extends PaymentTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    private void assertNothingChanged(Reservation reservation) {
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getCancelledAt()).isNull();
        verify(rounds, never()).release(anyLong(), anyInt());
        verify(rounds, never()).reserve(anyLong(), anyInt());
    }

    @Test
    @DisplayName("모름이면 503 이고 예약 상태와 정원이 모두 그대로다")
    void unknownChangesNothing() {
        Reservation reservation = given(pending());
        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.unknown("PG 무응답"));

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));

        assertNothingChanged(reservation);
    }

    @Test
    @DisplayName("모름 이후 재시도해서 성공하면 그때 확정된다 - 상태가 남아 있어 재시도가 가능하다")
    void retryAfterUnknownSucceeds() {
        Reservation reservation = given(pending());
        when(paymentService.confirm(any()))
                .thenReturn(PaymentApprovalResult.unknown("PG 거래없음"))
                .thenReturn(PaymentApprovalResult.success(AMOUNT));

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER))
                .isInstanceOf(ApiException.class);

        Reservation result = service.confirm(RESERVATION_ID, MEMBER);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(NOW);
    }
}
