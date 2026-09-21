package com.team1.expo.expo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색어를 LIKE 에 넣기 전 다듬는 규칙.
 *
 * <p>자연어 검색이 붙으면서 이 경로로 <b>LLM 이 만든 문자열</b>도 들어온다.
 * 사람이 친 값만 들어오던 때보다 방어가 더 필요해졌다.
 */
class KeywordNormalizationTest {

    @Test
    @DisplayName("공백만 있는 검색어는 조건에서 뺀다 - like '%   %' 는 아무것도 못 찾는다")
    void blankBecomesNull() {
        assertThat(ExpoQueryService.normalizeKeyword("   ")).isNull();
        assertThat(ExpoQueryService.normalizeKeyword("")).isNull();
        assertThat(ExpoQueryService.normalizeKeyword(null)).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백을 지운다")
    void trims() {
        assertThat(ExpoQueryService.normalizeKeyword("  푸드  ")).isEqualTo("푸드");
    }

    @Test
    @DisplayName("퍼센트를 막는다 - 사용자가 친 50% 가 와일드카드로 동작하면 안 된다")
    void escapesPercent() {
        assertThat(ExpoQueryService.normalizeKeyword("50%")).isEqualTo("50!%");
    }

    @Test
    @DisplayName("언더스코어도 한 글자 와일드카드라 막는다")
    void escapesUnderscore() {
        assertThat(ExpoQueryService.normalizeKeyword("a_b")).isEqualTo("a!_b");
    }

    @Test
    @DisplayName("이스케이프 문자 자체를 먼저 막는다 - 나중에 하면 이중 이스케이프가 된다")
    void escapesTheEscapeCharacterFirst() {
        assertThat(ExpoQueryService.normalizeKeyword("!%")).isEqualTo("!!!%");
    }
}
