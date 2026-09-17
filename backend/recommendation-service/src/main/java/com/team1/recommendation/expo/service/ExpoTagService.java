package com.team1.recommendation.expo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.recommendation.expo.entity.ExpoTag;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import com.team1.recommendation.internal.dto.ExpoPublishedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ExpoTagService {

    private static final Logger log = LoggerFactory.getLogger(ExpoTagService.class);

    private final ExpoTagRepository repository;
    private final RestClient geminiClient;
    private final String geminiApiKey;
    private final ObjectMapper objectMapper;

    public ExpoTagService(ExpoTagRepository repository,
                          @Qualifier("geminiRestClient") RestClient geminiClient,
                          @Value("${gemini.api-key}") String geminiApiKey,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.geminiClient = geminiClient;
        this.geminiApiKey = geminiApiKey;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleExpoPublished(ExpoPublishedRequest request) {
        log.info("expo-published received expoId={}", request.expoId());
        // 이미 태깅된 경우 중복 저장 방지
        boolean alreadyTagged = repository.findByExpoId(request.expoId()).stream()
                .anyMatch(t -> !"UNTAGGED".equals(t.getTagValue()));
        if (alreadyTagged) return;

        // 기존 UNTAGGED 행 제거 후 Gemini 태깅 비동기 실행
        repository.deleteByExpoId(request.expoId());
        CompletableFuture.runAsync(() -> tagAsync(request));
    }

    private void tagAsync(ExpoPublishedRequest request) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("GEMINI_API_KEY not set, skipping tagging for expoId={}", request.expoId());
            saveFallback(request.expoId());
            return;
        }

        try {
            String prompt = """
                    다음 박람회 정보를 분석해 JSON으로만 응답하세요 (설명 없이):
                    제목: %s
                    설명: %s

                    응답 형식:
                    {"keywords":["키워드1","키워드2","키워드3","키워드4","키워드5"]}
                    """.formatted(
                    request.title() != null ? request.title() : "",
                    request.description() != null ? request.description() : "");

            String requestBody = """
                    {
                      "contents": [{"parts": [{"text": %s}]}],
                      "generationConfig": {"responseMimeType": "application/json"}
                    }
                    """.formatted(objectMapper.writeValueAsString(prompt));

            String responseBody = geminiClient.post()
                    .uri("/v1beta/models/gemini-1.5-flash:generateContent?key={key}", geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String jsonText = root.at("/candidates/0/content/parts/0/text").asText();
            JsonNode parsed = objectMapper.readTree(jsonText);
            List<String> keywords = objectMapper.readerForListOf(String.class)
                    .readValue(parsed.get("keywords"));

            if (keywords == null || keywords.isEmpty()) {
                saveFallback(request.expoId());
                return;
            }

            saveKeywords(request.expoId(), keywords);
            log.info("Gemini tagging done expoId={} keywords={}", request.expoId(), keywords);

        } catch (Exception e) {
            log.warn("Gemini tagging failed expoId={}, using fallback", request.expoId(), e);
            saveFallback(request.expoId());
        }
    }

    @Transactional
    private void saveKeywords(Long expoId, List<String> keywords) {
        LocalDateTime now = LocalDateTime.now();
        keywords.forEach(kw -> repository.save(ExpoTag.of(expoId, kw, null, now)));
    }

    @Transactional
    private void saveFallback(Long expoId) {
        repository.save(ExpoTag.of(expoId, "UNTAGGED", null, LocalDateTime.now()));
    }
}
