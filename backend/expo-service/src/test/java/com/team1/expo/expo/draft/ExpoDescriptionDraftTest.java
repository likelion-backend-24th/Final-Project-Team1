package com.team1.expo.expo.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.ai.GeminiClient;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * #267. 키워드로 소개글 초안을 만든다.
 *
 * <p>가장 중요한 건 <b>저장되지 않는다</b>는 것이다. 초안은 주최자가 읽고 고친 뒤
 * 저장 버튼을 눌러야 DB 에 들어간다 - 그 사람이 중간에 있다는 게 이 기능의 안전장치다.
 */
class ExpoDescriptionDraftTest extends ApiTestSupport {

    private String ownerToken;
    private long channelId;

    @MockitoBean
    private GeminiClient gemini;

    @Autowired
    private ExpoRepository expoRepository;

    @BeforeEach
    void setUp() {
        ownerToken = jwtFor(uniqueUserId(), "ORGANIZER");

        ResponseEntity<JsonNode> channel = post("/api/v1/channels",
                """
                {"name":"%s","description":"테스트 채널"}
                """.formatted(uniqueName()), ownerToken);
        channelId = channel.getBody().path("data").path("id").asLong();
        assertThat(channelId).as("채널 생성 실패: %s", channel.getBody()).isPositive();
    }

    private ResponseEntity<JsonNode> draft(String body, String token) {
        return post("/api/v1/channels/" + channelId + "/expos/description-draft", body, token);
    }

    private void givenDraft(String description) {
        when(gemini.isAvailable()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), any()))
                .thenReturn(new DescriptionDraftService.Draft(description));
    }

    @Test
    @DisplayName("키워드만으로 초안이 나온다")
    void draftsFromKeywords() {
        givenDraft("AI 와 스타트업이 만나는 자리입니다.");

        JsonNode data = draft("""
                {"keywords":["AI","스타트업","네트워킹"]}
                """, ownerToken).getBody().path("data");

        assertThat(data.path("applied").asBoolean()).isTrue();
        assertThat(data.path("description").asText()).isEqualTo("AI 와 스타트업이 만나는 자리입니다.");
    }

    @Test
    @DisplayName("이미 입력한 제목·분야가 프롬프트에 함께 들어간다")
    void sendsFilledFieldsToPrompt() {
        givenDraft("초안");

        draft("""
                {"keywords":["AI"],"title":"2026 테크 잡페어","category":"IT·전자","venue":"벡스코","region":"부산"}
                """, ownerToken);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(gemini)
                .generateJson(eq(DescriptionDraftService.FEATURE), prompt.capture(), any());
        assertThat(prompt.getValue())
                .contains("2026 테크 잡페어")
                .contains("IT·전자")
                .contains("벡스코")
                .contains("부산");
    }

    @Test
    @DisplayName("LLM 을 쓸 수 없어도 200 - applied=false 로 알려준다")
    void returnsNotAppliedWithoutLlm() {
        when(gemini.isAvailable()).thenReturn(false);

        ResponseEntity<JsonNode> response = draft("""
                {"keywords":["AI"]}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").path("applied").asBoolean()).isFalse();
        assertThat(response.getBody().path("data").path("description").isNull()).isTrue();
    }

    @Test
    @DisplayName("600자를 넘으면 잘라서 준다 - 폼이 감당할 분량이어야 한다")
    void capsLength() {
        givenDraft("가".repeat(700));

        JsonNode data = draft("""
                {"keywords":["AI"]}
                """, ownerToken).getBody().path("data");

        assertThat(data.path("description").asText()).hasSize(DescriptionDraftService.MAX_LENGTH);
    }

    @Test
    @DisplayName("모델이 빈 문장을 주면 applied=false")
    void returnsNotAppliedOnBlank() {
        givenDraft("   ");

        JsonNode data = draft("""
                {"keywords":["AI"]}
                """, ownerToken).getBody().path("data");

        assertThat(data.path("applied").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("남의 채널에는 404 - 존재를 드러내지 않는다")
    void rejectsOtherChannel() {
        String stranger = jwtFor(uniqueUserId(), "ORGANIZER");

        ResponseEntity<JsonNode> response = draft("""
                {"keywords":["AI"]}
                """, stranger);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("키워드가 비면 400")
    void rejectsEmptyKeywords() {
        ResponseEntity<JsonNode> response = draft("""
                {"keywords":[]}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("초안을 만들어도 DB 에는 아무것도 쓰이지 않는다")
    void writesNothing() {
        givenDraft("초안입니다");
        long before = expoRepository.count();

        draft("""
                {"keywords":["AI","스타트업"]}
                """, ownerToken);

        assertThat(expoRepository.count()).isEqualTo(before);
    }
}
