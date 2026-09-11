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
    /** 이미 현장 입장(체크인)이 끝난 예약의 취소 요청. */
    ALREADY_CHECKED_IN(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
