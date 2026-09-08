package com.team1.payment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class PaymentService {

    private final PaymentTransactionRepository repository;
    private final PgClient pgClient;
    private final PaymentIdGenerator paymentIdGenerator;
    private final Clock clock;

    public PaymentService(
            PaymentTransactionRepository repository,
            PgClient pgClient,
            PaymentIdGenerator paymentIdGenerator,
            Clock clock
    ) {
        this.repository = repository;
        this.paymentIdGenerator = paymentIdGenerator;
        this.pgClient = pgClient;
        this.clock = clock;
    }

    public PaymentTransaction createPending(Long refId, Integer amount) {

        return repository.findByRefId(refId)
                .orElseGet(() -> createNew(refId, amount));
    }

    public PaymentTransaction createNew(Long refId, Integer amount) {

        String paymentId = paymentIdGenerator.generate();
        PaymentTransaction paymentTransaction = PaymentTransaction.create(refId, paymentId, amount, clock.instant());

        try {
            PaymentTransaction saved = repository.save(paymentTransaction);
            pgClient.create(saved.getPaymentId(), saved.getAmount());

            return saved;

        } catch (DataIntegrityViolationException e) {

            return repository.findByRefId(refId).orElseThrow();
        }
    }

    public PaymentApprovalResult confirm(Long refId) {
        PaymentTransaction paymentTransaction = repository.findByRefId(refId).orElseThrow();

        try {
            PgInquiryResult result = pgClient.inquire(paymentTransaction.getPaymentId());

            return switch (result.status()) {

                case PAID -> {
                    if (!result.amount().equals(paymentTransaction.getAmount())) {
                        // 상태를 바꾸지 않는다 — PG는 PAID라고 했으니 FAILED로 기록하면 안 되고,
                        // 금액이 안 맞으니 PAID로 확정할 수도 없다. 사람이 확인할 때까지 그대로 둔다.
                        yield PaymentApprovalResult.amountMismatch();
                    }

                    paymentTransaction.markPaid(result.pgTransactionId(), result.responseCode(), clock.instant());
                    yield PaymentApprovalResult.success(paymentTransaction.getAmount());
                }

                case FAILED -> {
                    paymentTransaction.markFailed(result.responseCode(), result.failureReason(), clock.instant());
                    yield PaymentApprovalResult.failedConfirmed(result.failureReason());
                }

                case NOT_FOUND -> PaymentApprovalResult.unknown();
            };

        } catch (PgCommunicationException e) {

            return PaymentApprovalResult.unknown();
        }

    }
}
