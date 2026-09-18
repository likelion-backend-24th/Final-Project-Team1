package com.team1.expo.expo;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.expo.client.RecommendationNotifier;
import com.team1.expo.client.RoundClient;
import com.team1.expo.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #269. 소개문이 바뀌면 태그를 다시 만든다.
 *
 * <p>핵심은 두 번째·세 번째다 - 장소만 바꿨는데 재태깅이 나가면 하루 호출 상한을 버리는 것이고,
 * 그 상한은 태깅·알림·검색이 함께 쓴다.
 */
class ExpoRetagOnUpdateTest extends ApiTestSupport {

    private long channelId;
    private long expoId;
    private String ownerToken;

    // 공개 전환이 회차 존재를 예약-Service 에 묻는다. 여기서는 그 검사가 목적이 아니다.
    @MockitoBean
    private RoundClient roundClient;

    @MockitoBean
    private RecommendationNotifier recommendationNotifier;

    @BeforeEach
    void setUp() {
        ownerToken = jwtFor(uniqueUserId(), "ORGANIZER");

        ResponseEntity<JsonNode> channel = post("/api/v1/channels",
                """
                {"name":"%s","description":"테스트 채널"}
                """.formatted(uniqueName()), ownerToken);
        channelId = channel.getBody().path("data").path("id").asLong();
        assertThat(channelId).as("채널 생성 실패: %s", channel.getBody()).isPositive();

        ResponseEntity<JsonNode> expo = post(expoUrl(),
                """
                {"title":"테스트 박람회","category":"IT·전자","description":"설명","venue":"코엑스","region":"서울"}
                """, ownerToken);
        expoId = expo.getBody().path("data").path("id").asLong();
        assertThat(expoId).as("박람회 생성 실패: %s", expo.getBody()).isPositive();
    }

    private void publish() {
        when(roundClient.existsByExpo(anyLong())).thenReturn(true);
        ResponseEntity<JsonNode> published = post("/api/v1/expos/" + expoId + "/publication", "{}", ownerToken);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<JsonNode> update(String body) {
        return patch(expoUrl() + "/" + expoId, body, ownerToken);
    }

    @Test
    @DisplayName("소개문을 바꾸면 재태깅을 요청한다")
    void retagsWhenDescriptionChanged() {
        publish();

        assertThat(update("""
                {"description":"AI 와 스타트업을 주제로 한 박람회입니다"}
                """).getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(recommendationNotifier)
                .notifyExpoUpdated(eq(expoId), any(), eq("AI 와 스타트업을 주제로 한 박람회입니다"));
    }

    @Test
    @DisplayName("제목을 바꿔도 재태깅을 요청한다 - 태깅이 제목도 읽는다")
    void retagsWhenTitleChanged() {
        publish();

        update("""
                {"title":"2026 테크 잡페어"}
                """);

        verify(recommendationNotifier).notifyExpoUpdated(eq(expoId), eq("2026 테크 잡페어"), any());
    }

    @Test
    @DisplayName("장소만 바꾸면 재태깅하지 않는다 - LLM 호출 상한을 버리지 않는다")
    void doesNotRetagWhenOnlyVenueChanged() {
        publish();

        update("""
                {"venue":"벡스코","region":"부산"}
                """);

        verify(recommendationNotifier, never()).notifyExpoUpdated(anyLong(), any(), any());
    }

    @Test
    @DisplayName("같은 소개문을 다시 보내면 재태깅하지 않는다")
    void doesNotRetagWhenDescriptionUnchanged() {
        publish();

        update("""
                {"description":"설명"}
                """);

        verify(recommendationNotifier, never()).notifyExpoUpdated(anyLong(), any(), any());
    }

    @Test
    @DisplayName("공개 전(HIDDEN) 박람회는 재태깅하지 않는다 - 추천·검색에 나오지 않는다")
    void doesNotRetagHiddenExpo() {
        update("""
                {"description":"아직 공개 전인 박람회입니다"}
                """);

        verify(recommendationNotifier, never()).notifyExpoUpdated(anyLong(), any(), any());
    }

    private String expoUrl() {
        return "/api/v1/channels/" + channelId + "/expos";
    }
}
