package com.team1.payment;

/**
 * PG(포트원) 실제 연동을 감춘 인터페이스. Mock/실제 구현을 갈아끼워도
 * 이 인터페이스를 쓰는 Reservation-Service·박람회-Service 코드는 바뀌지 않는다.

 * 세 메서드 모두 PG 호출 자체가 실패(무응답·Timeout)하면 결과를 반환하지 않고
 * {@link PgCommunicationException}을 던진다 — 호출자는 이걸 "모름"으로 분류한다.
 *
 * 실제 구현체는 PG 쓰기 API(create/cancel) 호출 시 paymentId를 Idempotency-Key로
 * 그대로 전달해야 한다 — 같은 paymentId로 재시도해도 PG 쪽에서 중복 처리되지 않게 하기 위함.
 */
public interface PgClient {

    PgCreateResult create(String paymentId, Integer amount);

    PgInquiryResult inquire(String paymentId);

    PgCancelResult cancel(String paymentId, Integer amount, String reason);
}
