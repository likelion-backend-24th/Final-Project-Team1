package com.team1.payment;

public record WebhookProcessResult(
        Long refId,
        PaymentApprovalResult approvalResult
) {
}
