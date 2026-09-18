package com.team1.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * 팀 공용 LLM 진입점.
 *
 * <p>호출부는 이 클래스만 보면 된다. 재시도·하루 호출 상한·응답 캐시는 여기서 처리한다.
 * 기능마다 호출 코드를 복사하면 무료 한도를 서로 갉아먹고, 어디서 얼마나 쓰는지 알 수 없다.
 *
 * <p>모든 메서드는 실패하면 {@code null} 이다(fail-open). LLM 때문에 사용자 요청이나
 * 배치가 멈추면 안 되고, 호출부는 대체 경로로 넘어가야 한다.
 */
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String modelPath;
    private final ObjectMapper objectMapper;
    private final AiCallBudget budget;
    private final AiResponseCache cache;
    private final int maxAttempts;

    GeminiClient(RestClient restClient, String apiKey, String modelPath, ObjectMapper objectMapper,
                 AiCallBudget budget, AiResponseCache cache, int maxAttempts) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.modelPath = modelPath;
        this.objectMapper = objectMapper;
        this.budget = budget;
        this.cache = cache;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** 키가 없거나 하루 상한을 넘겼으면 false. 프롬프트를 만들기 전에 물어볼 수 있다. */
    public boolean isAvailable() {
        return hasApiKey() && budget.hasRemaining();
    }

    /**
     * 프롬프트를 보내고 첫 번째 후보의 텍스트를 돌려준다.
     *
     * @param feature 호출한 기능 이름. 로그·호출량 집계 단위다(예: expo-tagging).
     */
    @Nullable
    public String generateText(String feature, String prompt) {
        if (!hasApiKey()) {
            log.warn("gemini.api-key not configured, skipping LLM call feature={}", feature);
            return null;
        }

        String cacheKey = AiResponseCache.keyOf(feature, modelPath, prompt);
        String cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("gemini cache hit feature={}", feature);
            return cached;
        }

        if (!budget.tryAcquire(feature)) {
            return null;
        }

        String text = callWithRetry(feature, prompt);
        if (text != null) {
            cache.put(cacheKey, text);
        }
        return text;
    }

    /**
     * JSON 으로만 답하게 하고 그 결과를 타입으로 받는다.
     *
     * <p>모델이 형식을 어기면 {@code null} 이다 - 기능마다 파싱 코드를 두지 않게 여기서 흡수한다.
     */
    @Nullable
    public <T> T generateJson(String feature, String prompt, Class<T> type) {
        String text = generateText(feature, prompt);
        if (text == null) return null;
        try {
            return objectMapper.readValue(text, type);
        } catch (Exception e) {
            log.warn("gemini response is not valid json feature={} reason={}", feature, e.getMessage());
            return null;
        }
    }

    @Nullable
    private String callWithRetry(String feature, String prompt) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startedAt = System.currentTimeMillis();
            try {
                String text = extractText(call(prompt));
                log.info("gemini call ok feature={} attempt={} ms={}",
                        feature, attempt, System.currentTimeMillis() - startedAt);
                return text;

            } catch (HttpClientErrorException e) {
                // 4xx 는 키·모델명·요청 형식 문제다. 다시 보내도 같은 답이라 즉시 끝낸다.
                log.warn("gemini call rejected feature={} status={}", feature, e.getStatusCode());
                return null;

            } catch (RuntimeException e) {
                log.warn("gemini call failed feature={} attempt={} ms={} reason={}",
                        feature, attempt, System.currentTimeMillis() - startedAt, e.getMessage());
            }
        }
        return null;
    }

    private String call(String prompt) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("responseMimeType", "application/json")
            ));
            return restClient.post()
                    .uri(modelPath)
                    // 키는 헤더로만 보낸다. URL 에 실으면 로그·프록시에 남는다.
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("gemini request build failed", e);
        }
    }

    @Nullable
    private String extractText(@Nullable String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("gemini returned an empty body");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String text = root.at("/candidates/0/content/parts/0/text").asText(null);
            if (text == null || text.isBlank()) {
                // 안전 필터에 걸리면 candidates 가 비어서 온다. 재시도할 값이 아니므로 빈 결과로 끝낸다.
                log.warn("gemini returned no text");
                return null;
            }
            return text;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("gemini response parsing failed", e);
        }
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
