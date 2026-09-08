package com.team1.reservation.reservation;

import com.team1.payment.PaymentApprovalResult;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.support.PaymentTestFixture;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReservationPaymentServiceTest extends PaymentTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    private void givenSuccess() {
        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.success(AMOUNT));
    }

    @Test
    @DisplayName("모듈이 성공을 반환하면 CONFIRMED 로 전이하고 confirmedAt 을 기록한다")
    void confirmsOnSuccess() {
        given(pending());
        givenSuccess();

        Reservation confirmed = service.confirm(RESERVATION_ID, MEMBER);

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("확정은 정원을 건드리지 않는다 - 정원은 예약을 만들 때 이미 차감됐다")
    void doesNotTouchCapacityOnConfirm() {
        given(pending());
        givenSuccess();

        service.confirm(RESERVATION_ID, MEMBER);

        verify(rounds, never()).reserve(anyLong(), anyInt());
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("금액 검증은 모듈이 한다 - 이 Service 는 금액을 대조하지 않는다")
    void delegatesAmountVerificationToModule() {
        given(pending());
        // 모듈이 예약 금액과 다른 값을 성공으로 돌려줘도 이 Service 는 그대로 확정한다.
        // 위변조 검증은 payment_transactions 를 소유한 모듈의 책임이기 때문이다.
        when(paymentService.confirm(any())).thenReturn(PaymentApprovalResult.success(AMOUNT + 9999));

        assertThat(service.confirm(RESERVATION_ID, MEMBER).getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("남의 예약이면 403 이고 결제 모듈을 호출하지 않는다")
    void rejectsOtherMembersReservation() {
        given(pending());

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, new AuthenticatedUser(999L, "USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("없는 예약은 404")
    void rejectsUnknownReservation() {
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("USER 가 아닌 Role 은 403, 미인증은 401 - 예약을 조회하지도 않는다")
    void rejectsNonMember() {
        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, new AuthenticatedUser(1L, "ORGANIZER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));

        verify(reservations, never()).findById(anyLong());
        verifyNoInteractions(paymentService);
    }
}
