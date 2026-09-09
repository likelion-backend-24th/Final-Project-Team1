package com.team1.payment;

/** 웹훅 서명 검증 실패. SDK 예외를 감싸 소비 Service 가 PortOne SDK 에 의존하지 않게 한다. */
public class WebhookVerificationFailedException extends RuntimeException {

    public WebhookVerificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
