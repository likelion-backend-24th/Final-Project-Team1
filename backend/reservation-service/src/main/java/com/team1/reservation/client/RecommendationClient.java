package com.team1.reservation.client;

import com.team1.reservation.common.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

/**
 * Recommendation-Service 로 행동 이벤트를 보낸다(#184).
 *
 * <p>fire-and-forget·fail-open 이다. 추천 서비스가 죽어 있어도 예약 확정은 그대로 끝나야 한다.
 */
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

    public void sendReservationConfirmed(Long userId, Long expoId, Long reservationId, String reservationNo) {
        // 다른 스레드로 넘어가면 TraceId 가 사라지므로 미리 꺼내 둔다
        String traceId = TraceId.get();
        CompletableFuture.runAsync(() -> {
            try {
                restClient.post()
                        .uri("/internal/v1/recommendations/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                        .header(TraceId.HEADER, traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new BehaviorEventBody(userId, expoId, "RESERVATION_CONFIRMED",
                                reservationId, reservationNo))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("recommendation event failed reservationId={} traceId={}", reservationId, traceId, e);
            }
        });
    }

    private record BehaviorEventBody(Long userId, Long expoId, String eventType,
                                     Long reservationId, String reservationNo) {
    }
}
