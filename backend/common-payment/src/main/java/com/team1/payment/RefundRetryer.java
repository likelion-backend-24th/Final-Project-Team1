package com.team1.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * 환불 재시도 1건을 실제로 실행한다(#125). PaymentService.cancel() 은 호출하지 않는다 -
 * 트랜잭션 전파 방식이 달라서(REQUIRES_NEW), cancel() 에 그대로 얹으면 같은 트랜잭션에서
 * 결제 상태를 다시 읽는 호출자(ReservationCancelService 등)가 갱신 전 값을 보게 된다.
 *
 * <p><b>절대 예외를 던지지 않는다.</b> 배치에서 한 건이 나머지를 멈추면 안 된다.
 */
@Component
public class RefundRetryer {

    private static final Logger log = LoggerFactory.getLogger(RefundRetryer.class);

    private final PaymentTransactionRepository payments;
    private final PgClient pgClient;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration backoff;

    public RefundRetryer(PaymentTransactionRepository payments,
                          PgClient pgClient,
                          Clock clock,
                          @Value("${scheduler.refund-retry.max-attempts}") int maxAttempts,
                          @Value("${scheduler.refund-retry.backoff}") Duration backoff) {
        this.payments = payments;
        this.pgClient = pgClient;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    /** 성공하면 true. 실패·이미 처리됨은 false 이고, 어느 경우에도 예외를 던지지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retry(Long paymentTransactionId) {
        PaymentTransaction tx = payments.findById(paymentTransactionId).orElse(null);
        if (tx == null || tx.getStatus() != PaymentStatus.REFUND_FAILED) {
            return false;
        }

        try {
            PgCancelResult result = pgClient.cancel(tx.getPaymentId(), tx.getAmount(), "refund retry");

            if (!result.success()) {
                tx.markRefundFailed(result.responseCode(), maxAttempts, backoff, clock.instant());
                logFailure(tx, result.responseCode());
                return false;
            }

            tx.markCancelled(clock.instant());
            log.info("refund retry ok refId={} paymentId={} attempts={}",
                    tx.getRefId(), tx.getPaymentId(), tx.getAttempts());
            return true;

        } catch (PgCommunicationException e) {
            tx.markRefundFailed(e.getMessage(), maxAttempts, backoff, clock.instant());
            logFailure(tx, e.getMessage());
            return false;
        }
    }

    private void logFailure(PaymentTransaction tx, String reason) {
        if (tx.getAttempts() >= maxAttempts) {

            // 자동 회수를 포기했다. CS 문의가 들어오면 이 로그로 찾는다.
            log.error("REFUND_RETRY_GAVE_UP refId={} paymentId={} attempts={} reason={}",
                    tx.getRefId(), tx.getPaymentId(), tx.getAttempts(), reason);
            return;
        }
        log.warn("REFUND_RETRY_FAILED refId={} paymentId={} attempts={} nextAttemptAt={} reason={}",
                tx.getRefId(), tx.getPaymentId(), tx.getAttempts(), tx.getNextAttemptAt(), reason);
    }
}
