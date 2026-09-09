package com.team1.payment;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PortOneWebhookVerifier {

    private final WebhookVerifier webhookVerifier;

    public PortOneWebhookVerifier(
            @Value("${portone.webhook-secret}")String webhookSecret
    ){
      this.webhookVerifier = new WebhookVerifier(webhookSecret);
    }


    public Webhook verify(
            String body,String webhookId,
            String webhookSignature,String webhookTimestamp)
    throws WebhookVerificationException{
        return webhookVerifier.verify(body,webhookId,webhookSignature,webhookTimestamp);
    }
}
