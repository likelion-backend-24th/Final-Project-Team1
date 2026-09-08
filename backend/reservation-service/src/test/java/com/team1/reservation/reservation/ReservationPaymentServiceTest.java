package com.team1.reservation.reservation;

import com.team1.payment.PgInquiryResult;
import com.team1.payment.PgPaymentStatus;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReservationPaymentServiceTest extends PaymentTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    private void givenPaid(int amount) {
        when(pgClient.inquire(PAYMENT_ID)).thenReturn(
                new PgInquiryResult(PgPaymentStatus.PAID, amount, "pg-tx-1", "0000", null));
    }

    @Test
    @DisplayName("결제가 확인되면 CONFIRMED 로 전이하고 confirmedAt 을 기록한다")
    void confirmsWhenPaid() {
        Reservation reservation = given(pending());
        givenPaid(AMOUNT);

        Reservation confirmed = service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID);

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("확정은 정원을 건드리지 않는다 - 정원은 예약을 만들 때 이미 차감됐다")
    void doesNotTouchCapacityOnConfirm() {
        given(pending());
        givenPaid(AMOUNT);

        service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID);

        verify(rounds, never()).reserve(anyLong(), anyInt());
        verify(rounds, never()).release(anyLong(), anyInt());
    }

    @Test
    @DisplayName("무료 회차(금액 0)도 같은 경로로 확정된다")
    void confirmsFreeReservation() {
        Reservation free = given(Reservation.create("R-0000-0000", ROUND_ID, EXPO_ID, USER_ID,
                "홍길동", "01012345678", 1, 0, NOW));
        givenPaid(0);

        assertThat(service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID).getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(free.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("남의 예약이면 403 이고 PG 를 조회하지 않는다")
    void rejectsOtherMembersReservation() {
        given(pending());

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, new AuthenticatedUser(999L, "USER"), PAYMENT_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pgClient);
    }

    @Test
    @DisplayName("없는 예약은 404")
    void rejectsUnknownReservation() {
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("USER 가 아닌 Role 은 403, 미인증은 401 - 예약을 조회하지도 않는다")
    void rejectsNonMember() {
        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, new AuthenticatedUser(1L, "ORGANIZER"), PAYMENT_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, null, PAYMENT_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));

        verify(reservations, never()).findById(anyLong());
        verify(pgClient, never()).inquire(anyString());
    }
}
