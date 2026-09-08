package com.team1.reservation.reservation;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 허용 전이 4개와, 종료 상태에서 나가는 전이가 전부 막히는지를 확인한다.
 */
class ReservationStatusTransitionTest {

    private static final Instant NOW = Instant.parse("2026-09-08T04:00:00Z");
    private static final Instant BEFORE_EXPIRY = NOW.plusSeconds(60);
    private static final Instant AFTER_EXPIRY = NOW.plus(Reservation.PAYMENT_WINDOW);

    private static Reservation pending() {
        return Reservation.create("R-4K7Q-W2M8", 1L, 10L, 100L,
                "홍길동", "01012345678", 2, 20000, NOW);
    }

    private static Reservation confirmed() {
        Reservation reservation = pending();
        reservation.confirm(BEFORE_EXPIRY);
        return reservation;
    }

    @Test
    @DisplayName("PENDING → CONFIRMED - 결제 승인 시각이 confirmedAt 에 남는다")
    void pendingToConfirmed() {
        Reservation reservation = pending();

        reservation.confirm(BEFORE_EXPIRY);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(BEFORE_EXPIRY);
        assertThat(reservation.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("PENDING → CANCELLED - 결제 실패")
    void pendingToCancelled() {
        Reservation reservation = pending();

        reservation.cancel(BEFORE_EXPIRY);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isEqualTo(BEFORE_EXPIRY);
    }

    @Test
    @DisplayName("PENDING → EXPIRED - 만료 시각이 지난 뒤에만 허용하고 cancelledAt 은 채우지 않는다")
    void pendingToExpired() {
        Reservation reservation = pending();

        reservation.expire(AFTER_EXPIRY);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("CONFIRMED → CANCELLED - 사용자 취소")
    void confirmedToCancelled() {
        Reservation reservation = confirmed();

        reservation.cancel(AFTER_EXPIRY);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isEqualTo(AFTER_EXPIRY);
        assertThat(reservation.getConfirmedAt()).isEqualTo(BEFORE_EXPIRY);
    }

    @Test
    @DisplayName("만료 시각이 지난 예약은 결제 승인을 거절한다")
    void rejectsConfirmAfterExpiry() {
        Reservation reservation = pending();

        assertThatThrownBy(() -> reservation.confirm(AFTER_EXPIRY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("만료 시각 전에는 EXPIRED 로 바꾸지 않는다 - 스케줄러 조회 조건 오류를 엔티티가 막는다")
    void rejectsExpireBeforeExpiry() {
        Reservation reservation = pending();

        assertThatThrownBy(() -> reservation.expire(BEFORE_EXPIRY))
                .isInstanceOf(ApiException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("이미 CONFIRMED 인 예약은 다시 승인하지 않는다")
    void rejectsDoubleConfirm() {
        Reservation reservation = confirmed();

        assertThatThrownBy(() -> reservation.confirm(BEFORE_EXPIRY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("CANCELLED 는 종료 상태다 - 승인·취소·만료 전부 거절한다")
    void cancelledIsTerminal() {
        Reservation reservation = pending();
        reservation.cancel(BEFORE_EXPIRY);

        assertThatThrownBy(() -> reservation.confirm(BEFORE_EXPIRY)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reservation.cancel(BEFORE_EXPIRY)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reservation.expire(AFTER_EXPIRY)).isInstanceOf(ApiException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("EXPIRED 는 종료 상태다 - 승인·취소·만료 전부 거절한다")
    void expiredIsTerminal() {
        Reservation reservation = pending();
        reservation.expire(AFTER_EXPIRY);

        assertThatThrownBy(() -> reservation.confirm(BEFORE_EXPIRY)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reservation.cancel(AFTER_EXPIRY)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reservation.expire(AFTER_EXPIRY)).isInstanceOf(ApiException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }
}
