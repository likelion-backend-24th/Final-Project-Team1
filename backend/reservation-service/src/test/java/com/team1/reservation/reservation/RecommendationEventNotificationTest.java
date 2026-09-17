package com.team1.reservation.reservation;

import com.team1.payment.PaymentApprovalResult;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.support.PaymentTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** #184 예약 확정 → 추천 서비스 이벤트. 실제로 확정된 경로에서만 나간다. */
class RecommendationEventNotificationTest extends PaymentTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    @Test
    @DisplayName("결제가 확정되면 회원·박람회·예약 ID·예약번호를 담아 추천 서비스에 알린다")
    void notifiesOnConfirm() {
        Reservation reservation = given(pending());
        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.success(AMOUNT));

        service.confirm(RESERVATION_ID, MEMBER);

        verify(recommendationNotifier).reservationConfirmed(
                USER_ID, EXPO_ID, reservation.getId(), reservation.getReservationNo());
    }

    @Test
    @DisplayName("이미 확정된 예약을 다시 확정하면 알리지 않는다 - 알림이 두 번 가면 안 된다")
    void doesNotNotifyOnIdempotentReplay() {
        Reservation reservation = pending();
        reservation.confirm(NOW);
        given(reservation);

        service.confirm(RESERVATION_ID, MEMBER);

        verifyNoInteractions(recommendationNotifier);
    }

    @Test
    @DisplayName("결제 실패로 취소되면 알리지 않는다")
    void doesNotNotifyOnPaymentFailure() {
        given(pending());
        when(paymentService.confirm(any()))
                .thenReturn(PaymentApprovalResult.failedConfirmed("CARD_DECLINED"));

        service.confirm(RESERVATION_ID, MEMBER);

        verifyNoInteractions(recommendationNotifier);
    }

    @Test
    @DisplayName("결제 결과를 모르면 알리지 않는다")
    void doesNotNotifyOnUnknown() {
        given(pending());
        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.unknown("timeout"));

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER)).isInstanceOf(ApiException.class);

        verifyNoInteractions(recommendationNotifier);
    }
}
