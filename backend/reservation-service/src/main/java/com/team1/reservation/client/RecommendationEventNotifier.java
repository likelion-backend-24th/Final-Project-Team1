package com.team1.reservation.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team1.reservation.common.TraceId;
import com.team1.reservation.config.AfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

@Component
public class RecommendationEventNotifier {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEventNotifier.class);

    private final RestClient client;
    private final String internalToken;
    private final AfterCommitExecutor afterCommit;

    public RecommendationEventNotifier(@Qualifier("recommendationRestClient") RestClient client,
                                       @Value("${internal.token}") String internalToken,
                                       AfterCommitExecutor afterCommit) {
        this.client = client;
        this.internalToken = internalToken;
        this.afterCommit = afterCommit;
    }

    /**
     * 예약 확정(#184). 추천 점수와 "예약 확정" 알림(#231)의 입력이 된다.
     *
     * <p>롤백될 확정을 알리면 없는 예약의 알림이 생기므로 커밋 뒤에만 보낸다.
     * 티켓 통지와 달리 재시도 큐는 없다. 빠져도 예약·입장에는 영향이 없고 알림 하나가 덜 올 뿐이다.
     */
    public void reservationConfirmed(Long userId, Long expoId, Long reservationId, String reservationNo) {
        EventBody body = new EventBody(userId, expoId, "RESERVATION_CONFIRMED", reservationId, reservationNo);
        afterCommit.execute(() -> send(body));
    }

    // fail-open: 실패해도 예약 확정은 그대로 유지됨
    public void notifyEvent(Long userId, Long expoId, String eventType) {
        send(new EventBody(userId, expoId, eventType, null, null));
    }

    private void send(EventBody body) {
        // 다른 스레드로 넘어가면 TraceId 가 사라지므로 미리 꺼내 둔다
        String traceId = TraceId.get();
        CompletableFuture.runAsync(() -> {
            try {
                client.post()
                        .uri("/internal/v1/recommendations/events")
                        .header("Authorization", "Bearer " + internalToken)
                        .header(TraceId.HEADER, traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("recommendation event failed userId={} expoId={} type={} traceId={}",
                        body.userId(), body.expoId(), body.eventType(), traceId, e);
            }
        });
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EventBody(Long userId, Long expoId, String eventType, Long reservationId, String reservationNo) {
    }
}
