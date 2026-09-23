package com.team1.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
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
    private final Duration retryDelay;
    private final String thinkingLevel;
    private final int thinkingBudget;

    GeminiClient(RestClient restClient, String apiKey, String modelPath, ObjectMapper objectMapper,
                 AiCallBudget budget, AiResponseCache cache, int maxAttempts, Duration retryDelay,
                 String thinkingLevel, int thinkingBudget) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.modelPath = modelPath;
        this.objectMapper = objectMapper;
        this.budget = budget;
        this.cache = cache;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelay = retryDelay;
        this.thinkingLevel = thinkingLevel;
        this.thinkingBudget = thinkingBudget;
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
                // 4xx 는 대개 키·모델명·요청 형식 문제라 다시 보내도 같은 답이다.
                // 429 만 예외다 - 한도는 시간이 지나면 풀리므로 다른 5xx 와 같이 다룬다.
                if (e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                    // 본문에 거부 사유가 들어 있다. 상태 코드만 남기면 모델명·필드 중 무엇이
                    // 문제인지 알 수 없어 추측으로 고치게 된다.
                    log.warn("gemini call rejected feature={} status={} body={}",
                            feature, e.getStatusCode(), e.getResponseBodyAsString());
                    return null;
                }
                log.warn("gemini call throttled feature={} attempt={}", feature, attempt);
                if (!sleepBeforeRetry(attempt)) {
                    return null;
                }

            } catch (HttpServerErrorException e) {
                // 5xx 는 서버가 1초 안에 거절한 것이다. 다시 보내는 값이 크고 비용은 거의 없다.
                log.warn("gemini call failed feature={} attempt={} status={} body={}",
                        feature, attempt, e.getStatusCode(), e.getResponseBodyAsString());
                if (!sleepBeforeRetry(attempt)) {
                    return null;
                }

            } catch (RuntimeException e) {
                // 여기 오는 건 대개 read-timeout 이다. 이미 10초를 쓴 뒤라 다시 보내면
                // 같은 답을 받으려고 사용자가 두 배로 기다린다. 한 번으로 끝내고 폴백에 맡긴다.
                log.warn("gemini call timed out feature={} attempt={} ms={} reason={}",
                        feature, attempt, System.currentTimeMillis() - startedAt, e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * 다음 시도까지 기다린다. 마지막 시도였으면 false.
     *
     * <p>503("잠시 뒤 다시") 을 받고 바로 다시 보내면 <b>429 를 우리가 만든다</b>. 시도마다
     * 대기를 두 배로 늘려 같은 초에 두 번 때리지 않게 한다.
     */
    private boolean sleepBeforeRetry(int attempt) {
        if (attempt >= maxAttempts) {
            return false;
        }
        long millis = retryDelay.toMillis() * attempt;
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String call(String prompt) {
        try {
            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            // Gemini 3·2.5 계열은 기본으로 추론을 한다. 문장에서 값을 뽑는 일에는 필요 없는 비용이고,
            // 사람이 기다리는 호출에서는 read-timeout 을 넘긴다. 키는 세대마다 다르다 -
            // Gemini 3 은 thinkingLevel(low·medium·high), 2.5 는 thinkingBudget(0 이면 끔).
            Map<String, Object> thinkingConfig = new LinkedHashMap<>();
            if (thinkingLevel != null && !thinkingLevel.isBlank()) {
                thinkingConfig.put("thinkingLevel", thinkingLevel.trim());
            }
            if (thinkingBudget >= 0) {
                thinkingConfig.put("thinkingBudget", thinkingBudget);
            }
            if (!thinkingConfig.isEmpty()) {
                generationConfig.put("thinkingConfig", thinkingConfig);
            }
            String body = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", generationConfig
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
