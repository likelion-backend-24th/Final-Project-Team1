package com.team1.payment;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class RefundRetryService {

    private static final Logger log = LoggerFactory.getLogger(RefundRetryService.class);

    private final PaymentTransactionRepository payments;
    private final RefundRetryer retryer;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public RefundRetryService(PaymentTransactionRepository payments,
                              RefundRetryer retryer,
                              Clock clock,
                              @Value("${scheduler.refund-retry.batch-size}") int batchSize,
                              @Value("${scheduler.refund-retry.max-attempts}") int maxAttempts
                              ){
        this.payments = payments;
        this.retryer = retryer;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** 재시도 시각이 된 환불을 보낸다. 반환값은 이번 주기에 성공한 건수다. */
    public int retryDue() {
        Instant now = clock.instant();
        List<PaymentTransaction> due =
                payments.findDueForRefundRetry(maxAttempts, now, PageRequest.of(0, batchSize));

        int succeeded = 0;
        for (PaymentTransaction tx : due) {
            // retryer.retry() 는 예외를 삼키지만, Transaction 경계에서 나는 것까지 막지는 못한다.
            try {
                if (retryer.retry(tx.getId())) {
                    succeeded++;
                }
            } catch (RuntimeException e) {
                log.error("REFUND_RETRY_BATCH_FAILED paymentTransactionId={} reason={}",
                        tx.getId(), e.toString());
            }
        }

        if (!due.isEmpty()) {
            log.info("refund retry cycle: due={} succeeded={} now={}", due.size(), succeeded, now);
        }
        return succeeded;
    }
}
