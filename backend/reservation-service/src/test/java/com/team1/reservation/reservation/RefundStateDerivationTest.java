package com.team1.reservation.reservation;

import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;
import com.team1.reservation.reservation.entity.RefundState;
import com.team1.reservation.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #82 의 환불 상태 조합표. 화면이 조합을 알 필요가 없도록 서버가 파생하므로,
 * 이 표가 곧 사용자에게 보이는 문구를 결정한다.
 */
class RefundStateDerivationTest {

    private static final int MAX_ATTEMPTS = 6;
    private static final Instant NOW = Instant.parse("2026-09-10T04:00:00Z");

    private PaymentTransaction payment(PaymentStatus status, int attempts) {
        PaymentTransaction payment = PaymentTransaction.create(1L, "BE24-01-TEST", 20000, NOW);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "attempts", attempts);
        return payment;
    }

    private RefundState derive(ReservationStatus reservation, PaymentStatus payment, int attempts) {
        return RefundState.of(reservation, payment(payment, attempts), MAX_ATTEMPTS);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"PENDING", "CONFIRMED", "EXPIRED"})
    @DisplayName("취소된 예약이 아니면 환불을 논할 상황이 아니다")
    void notApplicableUnlessCancelled(ReservationStatus status) {
        assertThat(derive(status, PaymentStatus.PAID, 0)).isEqualTo(RefundState.NOT_APPLICABLE);
        assertThat(derive(status, PaymentStatus.PENDING, 0)).isEqualTo(RefundState.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("결제가 없는 예약(무료 회차)은 NOT_APPLICABLE")
    void notApplicableWithoutPayment() {
        assertThat(RefundState.of(ReservationStatus.CANCELLED, null, MAX_ATTEMPTS))
                .isEqualTo(RefundState.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("결제 실패로 취소된 건은 돌려줄 돈이 없다")
    void notApplicableWhenPaymentFailed() {
        assertThat(derive(ReservationStatus.CANCELLED, PaymentStatus.FAILED, 0))
                .isEqualTo(RefundState.NOT_APPLICABLE);
        assertThat(derive(ReservationStatus.CANCELLED, PaymentStatus.PENDING, 0))
                .isEqualTo(RefundState.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("환불이 끝났으면 REFUNDED")
    void refunded() {
        assertThat(derive(ReservationStatus.CANCELLED, PaymentStatus.CANCELLED, 1))
                .isEqualTo(RefundState.REFUNDED);
    }

    @Test
    @DisplayName("취소됐는데 결제가 PAID 로 살아 있으면 기한이 지나 환불하지 않은 것이다")
    void notRefundable() {
        assertThat(derive(ReservationStatus.CANCELLED, PaymentStatus.PAID, 0))
                .isEqualTo(RefundState.NOT_REFUNDABLE);
    }

    @Test
    @DisplayName("재시도 중이면 REFUND_PENDING, 상한을 넘기면 REFUND_UNRESOLVED")
    void splitsRefundFailedByAttempts() {
        assertThat(derive(ReservationStatus.CANCELLED, PaymentStatus.REFUND_FAILED, 1))
                .isEqualTo(RefundState.REFUND_PENDING);
        assertThat(derive(ReservationStatus.CANCELLED, PaymentStatus.REFUND_FAILED, MAX_ATTEMPTS - 1))
                .isEqualTo(RefundState.REFUND_PENDING);

        // 여기서부터는 배치가 더 이상 집지 않는다. "처리 중" 이라고 하면 거짓말이 된다.
        assertThat(derive(ReservationStatus.CANCELLED, PaymentStatus.REFUND_FAILED, MAX_ATTEMPTS))
                .isEqualTo(RefundState.REFUND_UNRESOLVED);
        assertThat(derive(ReservationStatus.CANCELLED, PaymentStatus.REFUND_FAILED, MAX_ATTEMPTS + 3))
                .isEqualTo(RefundState.REFUND_UNRESOLVED);
    }
}
