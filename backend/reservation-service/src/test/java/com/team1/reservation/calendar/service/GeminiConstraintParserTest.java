package com.team1.reservation.calendar.service;

import com.team1.ai.GeminiClient;
import com.team1.reservation.calendar.dto.ScheduleConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 모델이 무엇을 뱉든 <b>시스템이 아는 카테고리만</b> 조건으로 나가야 한다 - SearchQueryParserTest 와
 * 같은 전제다. 이 기능(#캘린더 추천)이 카테고리를 아예 못 걸러줬던 문제를 고치는 변경이라, 여기서
 * mainStartHourKst 필드명이 프롬프트·레코드와 실제로 맞물리는지도 같이 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class GeminiConstraintParserTest {

    @Mock
    private GeminiClient gemini;

    private GeminiConstraintParser parser;

    private void setUp() {
        parser = new GeminiConstraintParser(gemini);
    }

    private void givenParsed(ScheduleConstraint parsed) {
        when(gemini.isAvailable()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), eq(ScheduleConstraint.class))).thenReturn(parsed);
    }

    @Test
    @DisplayName("시간과 카테고리를 함께 옮긴다")
    void mapsHourAndCategory() {
        setUp();
        givenParsed(new ScheduleConstraint(14, "IT·전자"));

        ScheduleConstraint result = parser.tryParse("오후 2시 이후 IT 박람회만");

        assertThat(result.mainStartHourKst()).isEqualTo(14);
        assertThat(result.category()).isEqualTo("IT·전자");
    }

    @Test
    @DisplayName("정해진 6개가 아닌 카테고리는 버린다")
    void dropsUnknownCategory() {
        setUp();
        givenParsed(new ScheduleConstraint(null, "자동차"));

        ScheduleConstraint result = parser.tryParse("자동차 박람회만");

        assertThat(result.category()).isNull();
    }

    @Test
    @DisplayName("카테고리가 null 이어도 예외 없이 통과한다")
    void allowsNullCategory() {
        setUp();
        givenParsed(new ScheduleConstraint(9, null));

        ScheduleConstraint result = parser.tryParse("오전 9시 이후");

        assertThat(result.mainStartHourKst()).isEqualTo(9);
        assertThat(result.category()).isNull();
    }

    @Test
    @DisplayName("키가 없으면 LLM 을 부르지 않는다")
    void fallsBackWithoutApiKey() {
        setUp();
        when(gemini.isAvailable()).thenReturn(false);

        ScheduleConstraint result = parser.tryParse("IT 박람회만");

        assertThat(result).isNull();
        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("해석에 실패하면 null 을 돌려준다 - 호출부가 정규식 폴백으로 넘어간다")
    void returnsNullWhenNotInterpreted() {
        setUp();
        when(gemini.isAvailable()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), eq(ScheduleConstraint.class))).thenReturn(null);

        assertThat(parser.tryParse("아무 말이나")).isNull();
    }

    @Test
    @DisplayName("빈 입력은 LLM 을 부르지 않는다")
    void skipsBlankInput() {
        setUp();
        assertThat(parser.tryParse("   ")).isNull();
        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }
}
