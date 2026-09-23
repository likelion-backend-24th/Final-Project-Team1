package com.team1.reservation.calendar.service;

import com.team1.ai.GeminiClient;
import com.team1.reservation.calendar.dto.ScheduleConstraint;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class GeminiConstraintParser {

    private static final String FEATURE = "calendar-constraint";

    /**
     * expo-service {@code ExpoCategories.ALLOWED} 와 같은 값. 서비스가 달라 상수를 공유하지
     * 못해 그대로 옮겨 적었다 - 저기서 분야가 추가/변경되면 여기도 같이 바꿔야 한다.
     */
    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("IT·전자", "식품·음료", "패션·뷰티", "교육·취업", "문화·예술", "기타");

    private static final String PROMPT = """
            사용자가 박람회 관람 일정을 짤 때 입력한 조건을 분석하세요.
            "몇 시 이후"라는 뜻이면 mainStartHourKst에 그 시(0~23, 한국시간 기준)를 넣으세요.
            분야를 말했으면 category에 다음 중 하나만 그대로 쓰세요, 없으면 null 로 두세요: %s
            해당 사항이 없거나 이해할 수 없으면 그 항목을 null로 두세요.
            추측하지 말고 문장에 실제로 있는 내용만 반영하세요.
            JSON으로만 응답하세요.

            사용자 입력 : %s
            응답 형식: {"mainStartHourKst": 14, "category": "IT·전자"}
            """;

    private final GeminiClient geminiClient;

    public GeminiConstraintParser(GeminiClient geminiClient){
        this.geminiClient=geminiClient;
    }

//    Gemini가 비활성이거나 실패/스키마 불일치면 null. 호출부가 정규식 풀백으로 넘어가야한다.
    public ScheduleConstraint tryParse(String constraint){
        if (constraint == null || constraint.isBlank()){
            return null;
        }
        if (!geminiClient.isAvailable()){
            return null;
        }
        String prompt = PROMPT.formatted(String.join(", ", ALLOWED_CATEGORIES), constraint);
        ScheduleConstraint parsed = geminiClient.generateJson(FEATURE, prompt, ScheduleConstraint.class);
        if (parsed == null) {
            return null;
        }
        // 모델 출력은 사용자 입력과 같은 등급이다 - 허용 목록에 없는 카테고리는 버린다.
        // null 을 먼저 거르는 이유: Set.of(...) 는 contains(null) 에 NPE 를 던진다.
        String category = parsed.category() != null && ALLOWED_CATEGORIES.contains(parsed.category())
                ? parsed.category() : null;
        return new ScheduleConstraint(parsed.mainStartHourKst(), category);
    }
}
