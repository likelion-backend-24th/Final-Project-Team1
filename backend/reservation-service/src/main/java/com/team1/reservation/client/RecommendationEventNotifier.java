package com.team1.reservation.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class RecommendationEventNotifier {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEventNotifier.class);

    private final RestClient client;
    private final String internalToken;

    public RecommendationEventNotifier(@Qualifier("recommendationRestClient") RestClient client,
                                       @Value("${internal.token}") String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    // fail-open: 실패해도 예약 확정은 그대로 유지됨
    public void notifyEvent(Long userId, Long expoId, String eventType) {
        CompletableFuture.runAsync(() -> {
            try {
                client.post()
                        .uri("/internal/v1/recommendations/events")
                        .header("Authorization", "Bearer " + internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("userId", userId, "expoId", expoId, "eventType", eventType))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("recommendation event failed userId={} expoId={} type={}", userId, expoId, eventType, e);
            }
        });
    }
}
