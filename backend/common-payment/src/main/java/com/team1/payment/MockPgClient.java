package com.team1.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!portone-live")
public class MockPgClient implements PgClient {

    private final Map<String, Integer> amountsByPaymentId = new ConcurrentHashMap<>();
    private final String storeId;
    private final String channelKey;

    public MockPgClient(
            @Value("${portone.store-id}") String storeId,
            @Value("${portone.channel-key}") String channelKey
    ) {
        this.storeId = storeId;
        this.channelKey = channelKey;
    }

    @Override
    public PgCreateResult create(String paymentId, Integer amount) {
        amountsByPaymentId.put(paymentId, amount);
        return new PgCreateResult(true, "0000");
    }

    @Override
    public PgInquiryResult inquire(String paymentId) {
        Integer amount = amountsByPaymentId.get(paymentId);
        return new PgInquiryResult(PgPaymentStatus.PAID, amount, "mock-" + paymentId, "0000", null,
                storeId, channelKey);
    }

    @Override
    public PgCancelResult cancel(String paymentId, Integer amount, String reason) {
        return new PgCancelResult(true, "0000");
    }
}