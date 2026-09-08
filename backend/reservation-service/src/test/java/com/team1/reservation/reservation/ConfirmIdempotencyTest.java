package com.team1.reservation.reservation;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.support.PaymentTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 확정 경로의 상태 3분기. 웹훅과 Client 요청이 같은 결제를 두고 경쟁하므로,
 * 이미 끝난 예약에 확정 요청이 다시 오는 것은 예외가 아니라 정상 흐름이다.
 */
class ConfirmIdempotencyTest extends PaymentTestFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    @Test
    @DisplayName("이미 CONFIRMED 면 재검증 없이 현재 상태를 그대로 반환한다")
    void alreadyConfirmedIsIdempotent() {
        Reservation reservation = pending();
        reservation.confirm(NOW);
        given(reservation);

        Reservation result = service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(NOW);

        // PG 를 다시 조회하면 불필요한 왕복이 생기고, 그 사이 응답이 흔들리면 확정을 뒤집을 수도 있다.
        verifyNoInteractions(pgClient);
    }

    @Test
    @DisplayName("웹훅이 먼저 확정한 예약에 Client 요청이 뒤늦게 와도 상태가 바뀌지 않는다")
    void lateClientRequestDoesNotOverwriteWebhookResult() {
        Instant webhookTime = NOW.minusSeconds(30);
        Reservation reservation = pending();
        reservation.confirm(webhookTime);
        given(reservation);

        Reservation result = service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID);

        assertThat(result.getConfirmedAt()).isEqualTo(webhookTime);
    }

    @Test
    @DisplayName("이미 CANCELLED 면 409 INVALID_STATE_TRANSITION")
    void alreadyCancelledIsConflict() {
        Reservation reservation = pending();
        reservation.cancel(NOW);
        given(reservation);

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        verifyNoInteractions(pgClient);
    }

    @Test
    @DisplayName("이미 EXPIRED 면 409 INVALID_STATE_TRANSITION")
    void alreadyExpiredIsConflict() {
        Reservation reservation = pending();
        reservation.expire(NOW.plus(Reservation.PAYMENT_WINDOW));
        given(reservation);

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, MEMBER, PAYMENT_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        verifyNoInteractions(pgClient);
    }
}
