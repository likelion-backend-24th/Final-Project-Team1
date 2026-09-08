package com.team1.expo.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

// ponytail: B파트 common-payment 완성 전 임시 stub.
// common-payment 모듈 완성 시 이 클래스를 제거하고 실제 구현체로 교체.
@Component
@ConditionalOnProperty(name = "portone.stub", havingValue = "true", matchIfMissing = true)
public class StubPortOneClient implements PortOneClient {

    private static final Logger log = LoggerFactory.getLogger(StubPortOneClient.class);

    @Override
    public String createPaymentOrder(long amount, String noticeUrl) {
        String id = "stub-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[STUB] PortOne 결제 주문 생성 amount={} noticeUrl={} pgTransactionId={}", amount, noticeUrl, id);
        return id;
    }

    @Override
    public void refund(String pgTransactionId, int amount) {
        log.info("[STUB] PortOne 환불 pgTransactionId={} amount={}", pgTransactionId, amount);
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        log.info("[STUB] PortOne 웹훅 서명 검증 통과 (stub)");
        return true;
    }
}
