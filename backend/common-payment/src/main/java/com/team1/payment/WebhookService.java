package com.team1.payment;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

@Service
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

    public PaymentApprovalResult process(String body, String webhookId,
                                         String webhookSignature, String webhookTimestamp)
            throws WebhookVerificationException {

        Webhook webhook = webhookVerifier.verify(body, webhookId, webhookSignature, webhookTimestamp);

        if (webhookEventRepository.findByWebhookId(webhookId).isPresent()) {
            return PaymentApprovalResult.unknown("이미 처리한 웹훅");
        }

        if (!(webhook instanceof WebhookTransaction transaction)) {
            return PaymentApprovalResult.unknown("결제 관련 웹훅 아님");
        }

        String paymentId = transaction.getData().getPaymentId();

        WebhookEvent webhookEvent = WebhookEvent.receive(
                webhookId, paymentId,
                transaction.getClass().getSimpleName(), clock.instant());
        webhookEventRepository.save(webhookEvent);

        Optional<PaymentTransaction> paymentTransactionOptional =
                paymentTransactionRepository.findByPaymentId(paymentId);
        if (paymentTransactionOptional.isEmpty()){
            return PaymentApprovalResult.unknown("알 수 없는 paymentId");
        }
        PaymentTransaction paymentTransaction = paymentTransactionOptional.get();

        PaymentApprovalResult paymentApprovalResult = paymentService.confirm(paymentTransaction.getRefId());

        webhookEvent.markProcessed(clock.instant());

        return paymentApprovalResult;

    }

}
