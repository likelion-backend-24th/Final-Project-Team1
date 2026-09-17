package com.team1.recommendation.expo.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class InternalExpoClient {

    private static final Logger log = LoggerFactory.getLogger(InternalExpoClient.class);

    private final RestClient client;
    private final String internalToken;

    public InternalExpoClient(@Qualifier("expoRestClient") RestClient client,
                              @Value("${internal.token}") String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    // fail-open: 실패하면 빈 목록 반환
    public List<ExpoSummary> listPublished() {
        try {
            List<Map<String, Object>> raw = client.get()
                    .uri("/internal/v1/expos")
                    .header("Authorization", "Bearer " + internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (raw == null) return List.of();
            return raw.stream()
                    .map(m -> new ExpoSummary(
                            toLong(m.get("expoId")),
                            (String) m.get("title"),
                            (String) m.get("description")))
                    .toList();
        } catch (Exception e) {
            log.warn("listPublished expos failed", e);
            return List.of();
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(val));
    }
}
