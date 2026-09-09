package com.team1.payment;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PortOneWebhookVerifier {

    private WebhookVerifier webhookVerifier;
    private final String webhookSecret;

    public PortOneWebhookVerifier(
            @Value("${portone.webhook-secret}")String webhookSecret){

      this.webhookSecret = webhookSecret;
    }

    public Webhook verify(String body,String webhookId,
            String webhookSignature,String webhookTimestamp)
    throws WebhookVerificationException{
        return getWebhookVerifier().verify(body,webhookId,
                webhookSignature,webhookTimestamp);
    }

    private synchronized WebhookVerifier getWebhookVerifier(){
        if (webhookVerifier == null){
            webhookVerifier = new WebhookVerifier(webhookSecret);
        }
        return webhookVerifier;
    }
}
