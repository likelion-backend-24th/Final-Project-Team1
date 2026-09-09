package com.team1.expo.domain.promotion;

import com.team1.payment.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpoWebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByWebhookId(String webhookId);
}
