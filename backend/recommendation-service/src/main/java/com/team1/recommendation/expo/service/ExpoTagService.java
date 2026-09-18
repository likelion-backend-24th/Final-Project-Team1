package com.team1.recommendation.expo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ai.AfterCommitRunner;
import com.team1.ai.GeminiClient;
import com.team1.recommendation.expo.entity.ExpoTag;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import com.team1.recommendation.internal.dto.ExpoPublishedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ExpoTagService {

    private static final Logger log = LoggerFactory.getLogger(ExpoTagService.class);

    private final ExpoTagRepository repository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final AfterCommitRunner afterCommitRunner;

    public ExpoTagService(ExpoTagRepository repository,
                          GeminiClient geminiClient,
                          ObjectMapper objectMapper,
                          AfterCommitRunner afterCommitRunner) {
        this.repository = repository;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.afterCommitRunner = afterCommitRunner;
    }

    @Transactional
    public void handleExpoPublished(ExpoPublishedRequest request) {
        log.info("expo-published received expoId={}", request.expoId());
        boolean alreadyTagged = repository.findByExpoId(request.expoId()).stream()
                .anyMatch(t -> !"UNTAGGED".equals(t.getTagValue()));
        if (alreadyTagged) return;

        repository.deleteByExpoId(request.expoId());
        // 커밋 후 비동기 실행 — delete가 커밋되기 전에 tagAsync가 실행되는 경쟁 조건 방지
        afterCommitRunner.execute(() -> CompletableFuture.runAsync(() -> tagAsync(request)));
    }

    private void tagAsync(ExpoPublishedRequest request) {
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

            String text = geminiClient.generateText(prompt);
            if (text == null) {
                saveFallback(request.expoId());
                return;
            }

            JsonNode parsed = objectMapper.readTree(text);
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

    private void saveKeywords(Long expoId, List<String> keywords) {
        LocalDateTime now = LocalDateTime.now();
        keywords.forEach(kw -> repository.save(ExpoTag.of(expoId, kw, null, now)));
    }

    private void saveFallback(Long expoId) {
        repository.save(ExpoTag.of(expoId, "UNTAGGED", null, LocalDateTime.now()));
    }
}
