package com.team1.recommendation.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ai.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiMessageService {

    private static final Logger log = LoggerFactory.getLogger(GeminiMessageService.class);

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public GeminiMessageService(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 박람회 알림 문구를 Gemini로 생성한다. 실패 시 null 반환 → 호출부에서 템플릿 fallback.
     */
    @Nullable
    public String generateNotificationMessage(String expoTitle, List<String> matchedTags) {
        String prompt = """
                당신은 앱 푸시 알림 문구 작성자입니다.
                아래 정보를 바탕으로 자연스러운 한국어 알림 문구를 한 문장으로 작성하세요.
                조건: 60자 이내, 친근한 말투, 관심사 태그를 자연스럽게 1~2개 언급, JSON으로만 응답.

                박람회 이름: %s
                사용자 관심 태그: %s

                응답 형식: {"message": "알림 문구"}
                """.formatted(expoTitle, String.join(", ", matchedTags));
        try {
            String text = geminiClient.generateText(prompt);
            if (text == null) return null;

            JsonNode node = objectMapper.readTree(text);
            String message = node.path("message").asText(null);
            if (message == null || message.isBlank()) return null;

            String trimmed = message.trim();
            return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
        } catch (Exception e) {
            log.warn("Failed to generate notification message for expo='{}': {}", expoTitle, e.getMessage());
            return null;
        }
    }
}
