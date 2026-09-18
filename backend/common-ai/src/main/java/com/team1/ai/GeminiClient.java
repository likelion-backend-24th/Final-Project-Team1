package com.team1.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String modelPath;
    private final ObjectMapper objectMapper;

    GeminiClient(RestClient restClient, String apiKey, String modelPath, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.modelPath = modelPath;
        this.objectMapper = objectMapper;
    }

    /**
     * Gemini에 프롬프트를 보내고 첫 번째 후보의 텍스트를 반환한다.
     * API 키 미설정 또는 호출 실패 시 null 반환 (fail-open).
     */
    @Nullable
    public String generateText(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("gemini.api-key not configured, skipping LLM call");
            return null;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("responseMimeType", "application/json")
            ));
            String response = restClient.post()
                    .uri(modelPath)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            return root.at("/candidates/0/content/parts/0/text").asText(null);
        } catch (Exception e) {
            log.warn("Gemini call failed: {}", e.getMessage());
            return null;
        }
    }
}
