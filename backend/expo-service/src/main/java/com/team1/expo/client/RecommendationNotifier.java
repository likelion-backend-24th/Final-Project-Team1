package com.team1.expo.client;

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
public class RecommendationNotifier {

    private static final Logger log = LoggerFactory.getLogger(RecommendationNotifier.class);

    private final RestClient client;
    private final String internalToken;

    public RecommendationNotifier(@Qualifier("recommendationRestClient") RestClient client,
                                  @Value("${internal.token}") String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    // fail-open: 추천 서비스 실패해도 게시는 이미 완료됨
    public void notifyExpoPublished(Long expoId, String title, String description) {
        CompletableFuture.runAsync(() -> {
            try {
                client.post()
                        .uri("/internal/v1/recommendations/expo-published")
                        .header("Authorization", "Bearer " + internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("expoId", expoId,
                                     "title", title != null ? title : "",
                                     "description", description != null ? description : ""))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("recommendation expo-published failed expoId={}", expoId, e);
            }
        });
    }
}
