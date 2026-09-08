package com.team1.expo.domain.promotion;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String webhookId;

    private Long paymentId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;

    public static WebhookEvent received(String webhookId, Long paymentId, String eventType, Clock clock) {
        WebhookEvent e = new WebhookEvent();
        e.webhookId = webhookId;
        e.paymentId = paymentId;
        e.eventType = eventType;
        e.status = "RECEIVED";
        e.receivedAt = LocalDateTime.now(clock);
        return e;
    }

    public void markProcessed(Clock clock) {
        this.status = "PROCESSED";
        this.processedAt = LocalDateTime.now(clock);
    }
}
