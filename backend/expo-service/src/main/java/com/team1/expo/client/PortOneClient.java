package com.team1.expo.client;

public interface PortOneClient {

    /**
     * PortOne 결제 주문을 생성합니다.
     * @return pgTransactionId (merchant_uid)
     */
    String createPaymentOrder(long amount, String noticeUrl);

    void refund(String pgTransactionId, int amount);

    boolean verifyWebhookSignature(byte[] rawBody, String signature);
}
