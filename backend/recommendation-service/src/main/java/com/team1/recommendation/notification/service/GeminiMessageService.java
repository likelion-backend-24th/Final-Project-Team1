package com.team1.recommendation.notification.service;

import com.team1.ai.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiMessageService {

    private static final Logger log = LoggerFactory.getLogger(GeminiMessageService.class);

    /** 호출량 집계·로그 단위. */
    private static final String FEATURE = "notification-message";

    private static final int MAX_LENGTH = 60;

    private final GeminiClient geminiClient;

    public GeminiMessageService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    /** LLM 응답을 받는 그릇. 파싱은 common-ai 가 한다. */
    public record Message(String message) {
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

        Message parsed = geminiClient.generateJson(FEATURE, prompt, Message.class);
        if (parsed == null || parsed.message() == null || parsed.message().isBlank()) {
            log.debug("no notification message generated for expo='{}'", expoTitle);
            return null;
        }

        // 조건을 어기고 길게 답하는 경우가 있다. 알림창이 깨지지 않게 여기서 자른다.
        String trimmed = parsed.message().trim();
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }
}
