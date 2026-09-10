package com.team1.reservation.reservation.entity;

import com.team1.payment.PaymentStatus;

/*
 예약 상태와 결제 상태를 조합해 파생하는 환불 표시값. */
public enum RefundState {

    /** 환불을 논할 상황이 아니다. 결제 대기·확정·결제 실패·만료가 여기 해당한다. */
    NOT_APPLICABLE,

    REFUNDED,

    /** 환불을 요청했으나 PG 가 받지 못했다. 재시도 대상이며 아직 돈은 돌아가지 않았다. */
    REFUND_PENDING,

    /** 환불 기한이 지나 취소만 된 경우. 결제는 PAID 로 영구 유지되며 오류가 아니다. */
    NOT_REFUNDABLE;

    /** payment 가 null 이면 결제 자체가 없는 예약(무료 회차)이다. */
    public static RefundState of(ReservationStatus reservation, PaymentStatus payment) {
        if (reservation != ReservationStatus.CANCELLED || payment == null) {
            return NOT_APPLICABLE;
        }

        return switch (payment) {
            case CANCELLED -> REFUNDED;
            case REFUND_FAILED -> REFUND_PENDING;

            // 취소됐는데 결제가 살아 있다 = 기한이 지나 환불하지 않은 것이다.
            case PAID -> NOT_REFUNDABLE;

            // 결제가 끝나지 않았거나 실패했으면 돌려줄 돈이 없다.
            case PENDING, FAILED -> NOT_APPLICABLE;
        };
    }
}
