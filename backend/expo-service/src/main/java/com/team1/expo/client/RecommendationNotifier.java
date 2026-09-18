package com.team1.expo.client;

import com.team1.ai.AfterCommitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * recommendation-Service 로 나가는 통지. 전부 fail-open 이다 - 추천이 죽어도 박람회 등록·공개·수정은 성립한다.
 *
 * <p><b>모든 통지는 커밋 이후에 나간다.</b> 커밋 전에 보내면 롤백된 변경으로 태깅이 돌아
 * 없는 내용의 태그가 남는다. 트랜잭션이 없는 호출부에서는 즉시 실행된다.
 */
@Component
public class RecommendationNotifier {

    private static final Logger log = LoggerFactory.getLogger(RecommendationNotifier.class);

    private final RestClient client;
    private final String internalToken;
    private final AfterCommitRunner afterCommit;

    public RecommendationNotifier(@Qualifier("recommendationRestClient") RestClient client,
                                  @Value("${internal.token}") String internalToken,
                                  AfterCommitRunner afterCommit) {
        this.client = client;
        this.internalToken = internalToken;
        this.afterCommit = afterCommit;
    }

    /** 공개 직후 자동 태깅 트리거. */
    public void notifyExpoPublished(Long expoId, String title, String description) {
        send("/internal/v1/recommendations/expo-published", expoId, title, description, "expo-published");
    }

    /**
     * 제목·소개문이 바뀌면 태그를 다시 만들게 한다.
     * 태그는 소개문을 읽어 만들어지므로, 소개문만 바뀌고 태그가 그대로면 추천·검색이 옛 내용을 본다.
     */
    public void notifyExpoUpdated(Long expoId, String title, String description) {
        send("/internal/v1/recommendations/expos/" + expoId + "/retag",
                expoId, title, description, "retag");
    }

    private void send(String uri, Long expoId, String title, String description, String kind) {
        afterCommit.execute(() -> CompletableFuture.runAsync(() -> {
            try {
                client.post()
                        .uri(uri)
                        .header("Authorization", "Bearer " + internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("expoId", expoId,
                                     "title", title != null ? title : "",
                                     "description", description != null ? description : ""))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("recommendation {} failed expoId={}", kind, expoId, e);
            }
        }));
    }
}
