package com.team1.payment;

import org.springframework.stereotype.Component;

/**
 * 실제 PortOne 연동 전, A(Reservation)·C(Ticket)가 병행 개발할 수 있게 항상 성공을 반환하는 가짜 구현체.
 * #88에서 실제 PortOne 연동으로 교체한다.
 */
@Component
public class MockPgClient implements PgClient {

    @Override
    public PgInquiryResult inquire(String paymentId) {
        return new PgInquiryResult(PgPaymentStatus.PAID, null, "mock-" + paymentId, "0000", null);
    }

    @Override
    public PgCancelResult cancel(String paymentId, Integer amount, String reason) {
        return new PgCancelResult(true, "0000");
    }
}
