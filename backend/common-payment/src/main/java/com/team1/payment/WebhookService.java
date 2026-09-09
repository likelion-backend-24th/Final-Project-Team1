package com.team1.payment;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
@Transactional
public class WebhookService {
    private final PortOneWebhookVerifier webhookVerifier;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    public WebhookService(
            PortOneWebhookVerifier webhookVerifier,
            WebhookEventRepository webhookEventRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentService paymentService,
            Clock clock
    ) {
        this.webhookVerifier = webhookVerifier;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentService = paymentService;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.clock = clock;
    }

    public WebhookProcessResult process(String body, String webhookId,
                                        String webhookSignature, String webhookTimestamp) {

        Webhook webhook;
        try {
            webhook = webhookVerifier.verify(body, webhookId, webhookSignature, webhookTimestamp);
        } catch (WebhookVerificationException e) {
            // SDK 예외를 모듈 예외로 감싼다. 소비 Service 가 PortOne SDK 를 의존하지 않게 하기 위해서다.
            throw new WebhookVerificationFailedException("webhook signature verification failed", e);
        }

        if (!(webhook instanceof WebhookTransaction transaction)) {
            return new WebhookProcessResult(null, PaymentApprovalResult.ignored("결제 관련 웹훅 아님"));
        }

        String paymentId = transaction.getData().getPaymentId();
        Long knownRefId = paymentTransactionRepository.findByPaymentId(paymentId)
                .map(PaymentTransaction::getRefId)
                .orElse(null);

        if (webhookEventRepository.findByWebhookId(webhookId).isPresent()) {
            return new WebhookProcessResult(knownRefId, PaymentApprovalResult.alreadyProcessed());
        }

        WebhookEvent webhookEvent = WebhookEvent.receive(
                webhookId, paymentId,
                transaction.getClass().getSimpleName(), clock.instant());
        webhookEventRepository.save(webhookEvent);

        Optional<PaymentTransaction> paymentTransactionOptional = paymentTransactionRepository.findByPaymentId(paymentId);
        if (paymentTransactionOptional.isEmpty()) {
            return new WebhookProcessResult(null, PaymentApprovalResult.unknown("알 수 없는 paymentId"));
        }
        PaymentTransaction paymentTransaction = paymentTransactionOptional.get();

        PaymentApprovalResult approvalResult = paymentService.confirm(paymentTransaction.getRefId());

        webhookEvent.markProcessed(clock.instant());

        return new WebhookProcessResult(paymentTransaction.getRefId(), approvalResult);
    }
}
