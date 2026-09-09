package com.team1.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

@Service
public class PaymentService {

    private final PaymentTransactionRepository repository;
    private final PgClient pgClient;
    private final PaymentIdGenerator paymentIdGenerator;
    private final Clock clock;
    private final String storeId;
    private final String channelKey;

    public PaymentService(
            PaymentTransactionRepository repository,
            PgClient pgClient,
            PaymentIdGenerator paymentIdGenerator,
            Clock clock,
            @Value("${portone.store-id}") String storeId,
            @Value("${portone.channel-key}") String channelKey
    ) {
        this.repository = repository;
        this.paymentIdGenerator = paymentIdGenerator;
        this.pgClient = pgClient;
        this.clock = clock;
        this.storeId = storeId;
        this.channelKey = channelKey;
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

                    if (!Objects.equals(result.amount(), paymentTransaction.getAmount())) {
                        yield PaymentApprovalResult.amountMismatch();
                    }

                    if (!Objects.equals(result.storeId(), storeId) || !Objects.equals(result.channelKey(), channelKey)) {
                        yield PaymentApprovalResult.amountMismatch();
                    }

                    paymentTransaction.markPaid(result.pgTransactionId(), result.responseCode(), clock.instant());
                    yield PaymentApprovalResult.success(paymentTransaction.getAmount());
                }

                case FAILED -> {
                    paymentTransaction.markFailed(result.responseCode(), result.failureReason(), clock.instant());
                    yield PaymentApprovalResult.failedConfirmed(result.failureReason());
                }

                case NOT_FOUND -> PaymentApprovalResult.unknown("PG 거래없음");
            };

        } catch (PgCommunicationException e) {
            return PaymentApprovalResult.unknown("PG 무응답");
            }
        }
    }
