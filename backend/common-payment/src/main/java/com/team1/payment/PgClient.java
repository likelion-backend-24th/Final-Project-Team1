package com.team1.payment;

/**
 * PG(포트원) 실제 연동을 감춘 인터페이스. Mock/실제 구현을 갈아끼워도
 * 이 인터페이스를 쓰는 Reservation-Service·박람회-Service 코드는 바뀌지 않는다.

 * 두 메서드 모두 PG 호출 자체가 실패(무응답·Timeout)하면 결과를 반환하지 않고
 * {@link PgCommunicationException}을 던진다 — 호출자는 이걸 "모름"으로 분류한다.
 */
public interface PgClient {

    PgInquiryResult inquire(String paymentId);

    PgCancelResult cancel(String paymentId, Integer amount, String reason);
}
