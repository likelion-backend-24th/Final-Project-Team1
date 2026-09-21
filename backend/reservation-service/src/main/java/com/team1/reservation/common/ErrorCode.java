package com.team1.reservation.common;

import org.springframework.http.HttpStatus;


public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST),
    /** 취소 마감(회차 시작) 이후의 취소 요청. 상태 충돌이 아니라 기한 위반이라 400 이다. */
    CANCELLATION_DEADLINE_PASSED(HttpStatus.BAD_REQUEST),
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT),
    /** 회차가 이미 시작해 예약을 받지 않는다. 회차는 아직 진행 중이므로 404 가 아니다. */
    RESERVATION_CLOSED(HttpStatus.CONFLICT),
    /** 활성 예약(결제대기·확정)이 있어 회차를 수정·삭제할 수 없다. 모두 취소되면 다시 가능하다. */
    ROUND_HAS_RESERVATIONS(HttpStatus.CONFLICT),
    /** 이미 시작한 회차의 수정·삭제 요청. */
    ROUND_ALREADY_STARTED(HttpStatus.CONFLICT),
    /** 이미 현장 입장(체크인)이 끝난 예약의 취소 요청. */
    ALREADY_CHECKED_IN(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
