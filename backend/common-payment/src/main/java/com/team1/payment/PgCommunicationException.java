package com.team1.payment;

/**
 * PG 호출 자체가 실패(무응답·Timeout·5xx)했을 때 던진다.
 * 호출자는 이 예외를 "모름"으로 분류해 상태를 바꾸지 않고 fail-closed 처리한다.
 */
public class PgCommunicationException extends RuntimeException {

    public PgCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public PgCommunicationException(String message) {
        super(message);
    }
}
