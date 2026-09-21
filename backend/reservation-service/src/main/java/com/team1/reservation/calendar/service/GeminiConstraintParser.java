package com.team1.reservation.calendar.service;

import com.team1.ai.GeminiClient;
import com.team1.reservation.calendar.dto.ScheduleConstraint;
import org.springframework.stereotype.Component;

@Component
public class GeminiConstraintParser {

    private static final String FEATURE = "calendar-constraint";

    private static final String PROMPT = """
            사용자가 박람회 관람 일정을 짤 때 입력한 시간 제약을 분석하세요.
            "몇 시 이후"라는 뜻이면 minStartHourKst에 그 시(0~23, 한국시간 기준)를 넣으세요.
            시간제약이 없거나 이해할 수 없으면 minStartHourKst를 null로 두세요.
            추측하지 말고 문장에 실제로 있는 내용만 반영하세요.
            JSON으로만 응답하세요.
            
            사용자 입력 : %s
            응답 혁식: {"minStartHourKst": 14}
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
        String prompt = PROMPT.formatted(constraint);
        return geminiClient.generateJson(FEATURE,prompt,ScheduleConstraint.class);
    }
}
