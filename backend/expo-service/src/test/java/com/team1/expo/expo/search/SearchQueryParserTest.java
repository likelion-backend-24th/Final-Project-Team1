package com.team1.expo.expo.search;

import com.team1.ai.GeminiClient;
import com.team1.expo.expo.repository.ExpoQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #261. 검색 문장을 검색 필터로 옮긴다.
 *
 * <p>핵심은 가운데 세 개다 - 모델이 무엇을 뱉든 <b>시스템이 아는 값만</b> 필터로 나가야 한다.
 * 그 보장이 있어야 "LLM 이 없는 박람회를 지어낼 수 없다" 는 이 기능의 전제가 성립한다.
 */
@ExtendWith(MockitoExtension.class)
class SearchQueryParserTest {

    // KST 로 2026-09-18. 서버 시계를 UTC 로 고정해도 "오늘" 은 한국 날짜여야 한다.
    private static final Instant NOW = Instant.parse("2026-09-18T01:00:00Z");
    private static final List<String> REGIONS = List.of("부산", "서울");

    @Mock
    private GeminiClient gemini;
    @Mock
    private ExpoQueryRepository expoQueryRepository;

    private SearchQueryParser parser;

    @BeforeEach
    void setUp() {
        parser = new SearchQueryParser(gemini, expoQueryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenAvailable() {
        when(gemini.isAvailable()).thenReturn(true);
        when(expoQueryRepository.findPublishedRegions()).thenReturn(REGIONS);
    }

    private void givenParsed(SearchQueryParser.Parsed parsed) {
        givenAvailable();
        when(gemini.generateJson(eq(SearchQueryParser.FEATURE), anyString(),
                eq(SearchQueryParser.Parsed.class))).thenReturn(parsed);
    }

    @Test
    @DisplayName("지역·분야·날짜를 필터로 옮긴다")
    void mapsRegionCategoryAndDate() {
        givenParsed(new SearchQueryParser.Parsed(
                "부산", "IT·전자", null, "2026-09-19", "2026-09-19", null));

        SearchFilter filter = parser.parse("19일 부산 IT 박람회");

        assertThat(filter.region()).isEqualTo("부산");
        assertThat(filter.category()).isEqualTo("IT·전자");
        assertThat(filter.dateFrom()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(filter.hasDateRange()).isTrue();
    }

    @Test
    @DisplayName("목록에 없는 지역은 조건에서 버린다 - 400 이 아니다")
    void dropsUnknownRegion() {
        givenParsed(new SearchQueryParser.Parsed(
                "제주", "IT·전자", null, null, null, null));

        SearchFilter filter = parser.parse("제주 IT 박람회");

        assertThat(filter.region()).isNull();
        assertThat(filter.category()).isEqualTo("IT·전자");
    }

    @Test
    @DisplayName("정해진 6개가 아닌 분야는 조건에서 버린다")
    void dropsUnknownCategory() {
        givenParsed(new SearchQueryParser.Parsed(
                "서울", "자동차", null, null, null, null));

        SearchFilter filter = parser.parse("서울 자동차 박람회");

        assertThat(filter.category()).isNull();
        assertThat(filter.region()).isEqualTo("서울");
    }

    @Test
    @DisplayName("끝이 시작보다 빠른 날짜는 날짜 조건을 통째로 버린다")
    void dropsInvertedDateRange() {
        givenParsed(new SearchQueryParser.Parsed(
                "서울", null, null, "2026-09-20", "2026-09-19", null));

        SearchFilter filter = parser.parse("서울 박람회");

        assertThat(filter.hasDateRange()).isFalse();
        assertThat(filter.dateFrom()).isNull();
    }

    @Test
    @DisplayName("3개월을 넘는 기간은 잘라낸다")
    void capsLongRange() {
        givenParsed(new SearchQueryParser.Parsed(
                null, null, null, "2026-09-19", "2027-09-19", null));

        SearchFilter filter = parser.parse("내년까지 열리는 박람회");

        assertThat(filter.dateTo()).isEqualTo(LocalDate.of(2026, 9, 19).plusDays(92));
    }

    @Test
    @DisplayName("날짜 형식이 깨지면 날짜 조건만 버리고 나머지는 쓴다")
    void dropsUnparsableDate() {
        givenParsed(new SearchQueryParser.Parsed(
                "부산", null, null, "다음 주", null, null));

        SearchFilter filter = parser.parse("다음 주 부산 박람회");

        assertThat(filter.hasDateRange()).isFalse();
        assertThat(filter.region()).isEqualTo("부산");
    }

    @Test
    @DisplayName("무료를 읽어 paid=false 로 옮긴다")
    void mapsFree() {
        givenParsed(new SearchQueryParser.Parsed(null, null, false, null, null, null));

        assertThat(parser.parse("무료로 갈만한 박람회").paid()).isFalse();
    }

    @Test
    @DisplayName("키가 없으면 LLM 을 부르지 않고 문장을 키워드로 넘긴다")
    void fallsBackWithoutApiKey() {
        when(gemini.isAvailable()).thenReturn(false);

        SearchFilter filter = parser.parse("19일 부산 IT 박람회");

        assertThat(filter.keyword()).isEqualTo("19일 부산 IT 박람회");
        assertThat(filter.isEmpty()).isTrue();
        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("해석이 실패하면 문장을 키워드로 넘긴다 - 검색이 막히지 않는다")
    void fallsBackWhenNotInterpreted() {
        givenAvailable();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(null);

        assertThat(parser.parse("아무 말이나").keyword()).isEqualTo("아무 말이나");
    }

    @Test
    @DisplayName("하나도 못 뽑으면 원문을 키워드로 돌려준다")
    void fallsBackWhenNothingExtracted() {
        givenParsed(new SearchQueryParser.Parsed(null, null, null, null, null, null));

        assertThat(parser.parse("음 글쎄").keyword()).isEqualTo("음 글쎄");
    }

    @Test
    @DisplayName("모든 박람회에 해당하는 말은 키워드에서 뺀다")
    void stripsGenericWordsFromKeyword() {
        // 이게 남으면 조건을 더 줄수록 결과가 좁아진다 - 제목에 '박람회' 가 없는 것이 다 빠진다.
        givenParsed(new SearchQueryParser.Parsed(null, "IT·전자", true, null, null, "벡스코 박람회"));

        assertThat(parser.parse("벡스코 유료 IT 박람회").keyword()).isEqualTo("벡스코");
    }

    @Test
    @DisplayName("일반 명사만 남으면 키워드가 없는 것과 같다")
    void dropsKeywordMadeOnlyOfGenericWords() {
        givenParsed(new SearchQueryParser.Parsed(null, null, false, null, null, "박람회 전시회"));

        SearchFilter filter = parser.parse("무료 박람회");

        assertThat(filter.keyword()).isNull();
        assertThat(filter.paid()).isFalse();
    }

    @Test
    @DisplayName("프롬프트에 오늘 요일을 넣는다 - 날짜만으로는 '이번 주' 를 못 잡는다")
    void promptCarriesDayOfWeek() {
        givenParsed(new SearchQueryParser.Parsed(null, null, null, null, null, "아무거나"));

        parser.parse("이번 주 박람회");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(anyString(), prompt.capture(), any());
        assertThat(prompt.getValue()).contains("2026-09-18 금요일");
    }

    @Test
    @DisplayName("빈 검색어는 LLM 을 부르지 않는다")
    void skipsBlankQuery() {
        SearchFilter filter = parser.parse("   ");

        assertThat(filter.keyword()).isNull();
        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }
}
