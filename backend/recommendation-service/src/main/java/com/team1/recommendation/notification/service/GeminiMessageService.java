package com.team1.recommendation.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class GeminiMessageService {

    private static final Logger log = LoggerFactory.getLogger(GeminiMessageService.class);

    private final RestClient geminiClient;
    private final String geminiApiKey;
    private final ObjectMapper objectMapper;

    public GeminiMessageService(@Qualifier("geminiRestClient") RestClient geminiClient,
                                @Value("${gemini.api-key}") String geminiApiKey,
                                ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.geminiApiKey = geminiApiKey;
        this.objectMapper = objectMapper;
    }

    /**
     * Gemini로 개인화 알림 문구를 생성한다.
     * 실패 시 null을 반환하며, 호출부에서 템플릿 문구로 fallback한다.
     */
    public String generateNotificationMessage(String expoTitle, List<String> matchedTags) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) return null;

        try {
            String tagList = String.join(", ", matchedTags);
            String prompt = """
                    당신은 앱 푸시 알림 문구 작성자입니다.
                    아래 정보를 바탕으로 자연스러운 한국어 알림 문구를 한 문장으로 작성하세요.
                    조건: 60자 이내, 친근한 말투, 관심사 태그를 자연스럽게 1~2개 언급, JSON으로만 응답.

                    박람회 이름: %s
                    사용자 관심 태그: %s

                    응답 형식: {"message": "알림 문구"}
                    """.formatted(expoTitle, tagList);

            String requestBody = """
                    {
                      "contents": [{"parts": [{"text": %s}]}],
                      "generationConfig": {"responseMimeType": "application/json"}
                    }
                    """.formatted(objectMapper.writeValueAsString(prompt));

            String responseBody = geminiClient.post()
                    .uri("/v1beta/models/gemini-flash-latest:generateContent?key={key}", geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String jsonText = root.at("/candidates/0/content/parts/0/text").asText();
            JsonNode parsed = objectMapper.readTree(jsonText);
            String message = parsed.get("message").asText();

            if (message == null || message.isBlank()) return null;
            return message;

        } catch (Exception e) {
            log.warn("Gemini notification message generation failed for expo='{}': {}", expoTitle, e.getMessage());
            return null;
        }
    }
}
