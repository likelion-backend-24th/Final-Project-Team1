package com.team1.settlement.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RestClientExpoDirectoryClient implements ExpoDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientExpoDirectoryClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public RestClientExpoDirectoryClient(RestClient expoRestClient,
                                         @Value("${internal.token}") String internalToken) {
        this.restClient = expoRestClient;
        this.internalToken = internalToken;
    }

    @Override
    public Map<Long, String> titles(Collection<Long> expoIds) {
        if (expoIds.isEmpty()) return Map.of();
        try {
            String query = expoIds.stream().map(id -> "expoIds=" + id).collect(Collectors.joining("&"));
            ExpoTitleItem[] found = restClient.get()
                    .uri("/internal/v1/expos/titles?" + query)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .retrieve()
                    .body(ExpoTitleItem[].class);
            if (found == null) return Map.of();
            return Arrays.stream(found)
                    .filter(t -> t.expoId() != null && t.title() != null)
                    .collect(Collectors.toMap(ExpoTitleItem::expoId, ExpoTitleItem::title, (a, b) -> a));
        } catch (Exception e) {
            log.warn("expoTitles failed count={}", expoIds.size(), e);
            return Map.of();
        }
    }

    @Override
    public Map<Long, String> categories(Collection<Long> expoIds) {
        if (expoIds.isEmpty()) return Map.of();
        try {
            String query = expoIds.stream().map(id -> "expoIds=" + id).collect(Collectors.joining("&"));
            ExpoCategoryItem[] found = restClient.get()
                    .uri("/internal/v1/expos/categories?" + query)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .retrieve()
                    .body(ExpoCategoryItem[].class);
            if (found == null) return Map.of();
            return Arrays.stream(found)
                    .filter(c -> c.expoId() != null && c.category() != null && !c.category().isBlank())
                    .collect(Collectors.toMap(ExpoCategoryItem::expoId, ExpoCategoryItem::category, (a, b) -> a));
        } catch (Exception e) {
            log.warn("expoCategories failed count={}", expoIds.size(), e);
            return Map.of();
        }
    }
}
