package com.team1.reservation.reservation.entity;

import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;

/*
 예약 상태와 결제 상태를 조합해 파생하는 환불 표시값. */
public enum RefundState {

    /** 환불을 논할 상황이 아니다. 결제 대기·확정·결제 실패·만료가 여기 해당한다. */
    NOT_APPLICABLE,

    REFUNDED,

    /** 환불을 요청했으나 아직 성공하지 못했다. 배치가 재시도 중이며 곧 돌아갈 수 있다. */
    REFUND_PENDING,

    /** 환불 기한이 지나 취소만 된 경우. 결제는 PAID 로 영구 유지되며 오류가 아니다. */
    NOT_REFUNDABLE;

    /**
     * @param payment     결제 행. 무료 회차처럼 결제 자체가 없으면 null
     * @param maxAttempts 환불 재시도 상한(`scheduler.refund-retry.max-attempts`).
     *                    Payment 모듈이 이 값으로 재시도 대상을 거르므로 같은 값을 봐야 한다
     */
    public static RefundState of(ReservationStatus reservation, PaymentTransaction payment, int maxAttempts) {
        if (reservation != ReservationStatus.CANCELLED || payment == null) {
            return NOT_APPLICABLE;
        }

        return switch (payment.getStatus()) {
            case CANCELLED -> REFUNDED;

            // 상태만으로는 재시도 중인지 포기했는지 구분되지 않는다.
            case REFUND_FAILED -> payment.getAttempts() >= maxAttempts ? REFUND_UNRESOLVED : REFUND_PENDING;

            // 취소됐는데 결제가 살아 있다 = 기한이 지나 환불하지 않은 것이다.
            case PAID -> NOT_REFUNDABLE;

            // 결제가 끝나지 않았거나 실패했으면 돌려줄 돈이 없다.
            case PENDING, FAILED -> NOT_APPLICABLE;
        };
    }
}
