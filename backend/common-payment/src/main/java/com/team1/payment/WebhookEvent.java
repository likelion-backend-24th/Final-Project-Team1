package com.team1.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "webhook_events")
@Getter
@NoArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_id", nullable = false, updatable = true)
    private String webhookId;

    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookEventStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public static  WebhookEvent receive(String webhookId, String paymentId, String eventType){
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.webhookId = webhookId;
        webhookEvent.paymentId = paymentId;
        webhookEvent.eventType = eventType;
        webhookEvent.status = WebhookEventStatus.RECEIVED;
        webhookEvent.receivedAt = Instant.now();

        return  webhookEvent;
    }

    public void markProcessed(){
        this.status = WebhookEventStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    public void markIgnored(){
        this.status = WebhookEventStatus.IGNORED;
        this.processedAt = Instant.now();
    }


}
