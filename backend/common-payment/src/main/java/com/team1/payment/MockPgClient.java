package com.team1.payment;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실제 PortOne 연동 전, A(Reservation)·C(Ticket)가 병행 개발할 수 있게 항상 성공을 반환하는 가짜 구현체.
 * #88에서 실제 PortOne 연동으로 교체한다.
 *
 * create()에서 받은 금액을 기억해뒀다가 inquire()에서 그대로 돌려준다 — 실제 PG는 결제된
 * 진짜 금액을 알려주므로, PaymentService의 금액 검증 로직이 Mock에서도 정상 동작하게 하기 위함.
 */
@Component
public class MockPgClient implements PgClient {

    private final Map<String, Integer> amountsByPaymentId = new ConcurrentHashMap<>();

    @Override
    public PgCreateResult create(String paymentId, Integer amount) {
        amountsByPaymentId.put(paymentId, amount);
        return new PgCreateResult(true, "0000");
    }

    @Override
    public PgInquiryResult inquire(String paymentId) {
        Integer amount = amountsByPaymentId.get(paymentId);
        return new PgInquiryResult(PgPaymentStatus.PAID, amount, "mock-" + paymentId, "0000", null);
    }

    @Override
    public PgCancelResult cancel(String paymentId, Integer amount, String reason) {
        return new PgCancelResult(true, "0000");
    }
}
