package com.team1.ticket.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

@Component
public class RecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public RecommendationClient(@Qualifier("recommendationRestClient") RestClient restClient,
                                @Value("${internal.token}") String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    // fail-open fire-and-forget: 실패해도 체크인은 그대로 유지된다
    public void sendCheckinEvent(Long userId, Long expoId) {
        CompletableFuture.runAsync(() -> {
            try {
                restClient.post()
                        .uri("/internal/v1/recommendations/events")
                        .header("Authorization", "Bearer " + internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new BehaviorEventBody(userId, expoId, "CHECKED_IN"))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("recommendation event failed userId={} expoId={}", userId, expoId, e);
            }
        });
    }

    private record BehaviorEventBody(Long userId, Long expoId, String eventType) {}
}
