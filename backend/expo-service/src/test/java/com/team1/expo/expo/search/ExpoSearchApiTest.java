package com.team1.expo.expo.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.ai.GeminiClient;
import com.team1.expo.client.RoundClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #262. 자연어 검색 엔드포인트.
 *
 * <p>핵심은 두 가지다 - 해석 결과가 응답에 실려야 화면이 조건을 보여줄 수 있고,
 * <b>LLM 이 죽어도 검색은 200 으로 나가야</b> 한다.
 *
 * <p>Test Container 를 다른 Test 클래스와 공유하므로 남이 남긴 박람회가 같은 DB 에 있다.
 * 지역과 제목에 이 실행에서만 쓰는 값을 넣어 내 데이터만 걸리게 한다.
 */
class ExpoSearchApiTest extends ApiTestSupport {

    private String ownerToken;
    private long channelId;
    private String region;
    private String foodToken;
    private long itExpoId;

    @MockitoBean
    private GeminiClient gemini;

    // 유료·무료 판정은 예약-Service 몫이다. 여기서는 그 검사가 목적이 아니다.
    @MockitoBean
    private RoundClient roundClient;

    @BeforeEach
    void setUp() {
        ownerToken = jwtFor(uniqueUserId(), "ORGANIZER");
        region = uniqueName();
        foodToken = uniqueName();

        ResponseEntity<JsonNode> channel = post("/api/v1/channels",
                """
                {"name":"%s","description":"테스트 채널"}
                """.formatted(uniqueName()), ownerToken);
        channelId = channel.getBody().path("data").path("id").asLong();
        assertThat(channelId).as("채널 생성 실패: %s", channel.getBody()).isPositive();

        itExpoId = createAndPublish("IT 박람회", "IT·전자");
        createAndPublish(foodToken + " 페어", "식품·음료");
    }

    private long createAndPublish(String title, String category) {
        ResponseEntity<JsonNode> created = post("/api/v1/channels/" + channelId + "/expos",
                """
                {"title":"%s","category":"%s","region":"%s","venue":"장소","description":"설명"}
                """.formatted(title, category, region), ownerToken);
        long expoId = created.getBody().path("data").path("id").asLong();
        assertThat(expoId).as("박람회 생성 실패: %s", created.getBody()).isPositive();

        when(roundClient.existsByExpo(expoId)).thenReturn(true);
        ResponseEntity<JsonNode> published =
                post("/api/v1/expos/" + expoId + "/publication", "{}", ownerToken);
        assertThat(published.getStatusCode()).as("공개 실패: %s", published.getBody())
                .isEqualTo(HttpStatus.OK);
        return expoId;
    }

    private void givenRoundsOn(Long... expoIds) {
        when(roundClient.expoIdsWithRoundsBetween(anyList(), any(), any(), anyBoolean()))
                .thenReturn(Set.of(expoIds));
    }

    /** 경로를 그대로 넘긴다. 공백 같은 문자는 RestTemplate 이 인코딩한다 - 미리 인코딩하면 이중이 된다. */
    private ResponseEntity<JsonNode> search(String query, String... extra) {
        return get("/api/v1/expos/search?q=" + query + String.join("", extra), null);
    }

    private void givenInterpreted(SearchQueryParser.Parsed parsed) {
        when(gemini.isAvailable()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(parsed);
    }

    @Test
    @DisplayName("문장을 해석해 지역·분야로 걸러낸다")
    void filtersByInterpretedFilter() {
        givenInterpreted(new SearchQueryParser.Parsed(region, "IT·전자", null, null, null, null));

        ResponseEntity<JsonNode> response = search("IT 박람회");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode expos = response.getBody().path("data").path("expos");
        assertThat(expos).hasSize(1);
        assertThat(expos.get(0).path("title").asText()).isEqualTo("IT 박람회");
    }

    @Test
    @DisplayName("해석 결과를 응답에 실어 화면이 보여줄 수 있게 한다")
    void returnsInterpretation() {
        givenInterpreted(new SearchQueryParser.Parsed(region, "식품·음료", null, null, null, null));

        JsonNode data = search("음식 박람회").getBody().path("data");

        assertThat(data.path("aiApplied").asBoolean()).isTrue();
        assertThat(data.path("interpreted").path("region").asText()).isEqualTo(region);
        assertThat(data.path("interpreted").path("category").asText()).isEqualTo("식품·음료");
    }

    @Test
    @DisplayName("LLM 을 쓸 수 없어도 200 - 문장을 키워드로 검색한다")
    void searchesWithoutLlm() {
        when(gemini.isAvailable()).thenReturn(false);

        ResponseEntity<JsonNode> response = search(foodToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = response.getBody().path("data");
        assertThat(data.path("aiApplied").asBoolean()).isFalse();
        assertThat(data.path("expos")).hasSize(1);
        assertThat(data.path("expos").get(0).path("title").asText()).isEqualTo(foodToken + " 페어");
    }

    @Test
    @DisplayName("검색어가 비면 400")
    void rejectsBlankQuery() {
        assertThat(search("   ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("날짜를 말하면 그 기간에 회차가 있는 박람회만 남는다")
    void filtersByDateRange() {
        givenInterpreted(new SearchQueryParser.Parsed(region, null, null, "2026-09-19", "2026-09-19", null));
        givenRoundsOn(itExpoId);

        JsonNode expos = search("19일 박람회").getBody().path("data").path("expos");

        assertThat(expos).hasSize(1);
        assertThat(expos.get(0).path("title").asText()).isEqualTo("IT 박람회");
    }

    @Test
    @DisplayName("KST 하루가 UTC 경계로 바뀌어 넘어가고, 예약 가능한 회차만 본다")
    void convertsKstDayToUtcBoundary() {
        givenInterpreted(new SearchQueryParser.Parsed(region, null, null, "2026-09-19", "2026-09-19", null));
        givenRoundsOn();

        search("19일 박람회");

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(roundClient).expoIdsWithRoundsBetween(anyList(), from.capture(), to.capture(), eq(true));

        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-09-18T15:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-09-19T15:00:00Z"));
    }

    @Test
    @DisplayName("회차 조회가 실패하면 503 - 날짜 조건을 조용히 빼지 않는다")
    void failsClosedWhenRoundLookupFails() {
        givenInterpreted(new SearchQueryParser.Parsed(region, null, null, "2026-09-19", "2026-09-19", null));
        when(roundClient.expoIdsWithRoundsBetween(anyList(), any(), any(), anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE));

        assertThat(search("19일 박람회").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("날짜를 말하지 않으면 회차를 조회하지 않는다")
    void skipsRoundLookupWithoutDate() {
        givenInterpreted(new SearchQueryParser.Parsed(region, "IT·전자", null, null, null, null));

        search("IT 박람회");

        verify(roundClient, never()).expoIdsWithRoundsBetween(anyList(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("페이지 정보가 meta 에 담긴다")
    void includesPageMeta() {
        givenInterpreted(new SearchQueryParser.Parsed(region, null, null, null, null, null));

        JsonNode body = search("박람회", "&page=1&size=1").getBody();

        assertThat(body.path("data").path("expos")).hasSize(1);
        assertThat(body.path("meta").path("page").asInt()).isEqualTo(1);
        assertThat(body.path("meta").path("size").asInt()).isEqualTo(1);
        assertThat(body.path("meta").path("totalElements").asInt()).isEqualTo(2);
    }
}
