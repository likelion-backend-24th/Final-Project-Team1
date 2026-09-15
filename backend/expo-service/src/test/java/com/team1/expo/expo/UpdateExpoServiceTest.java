package com.team1.expo.expo;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.expo.client.RoundClient;
import com.team1.expo.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * S9-1. 주최자용 조회와 부분 수정.
 * 핵심은 두 가지다 - 주최자는 자기 HIDDEN 박람회를 읽을 수 있어야 하고,
 * 방문자에게는 그 존재가 드러나면 안 된다.
 */
class UpdateExpoServiceTest extends ApiTestSupport {

    private long channelId;
    private long expoId;
    // 한 주최자는 채널을 하나만 가진다. Test 마다 새 주최자를 써야 @BeforeEach 가 409 로 깨지지 않는다.
    private String ownerToken;
    private String otherToken;

    // 공개 전환은 회차 존재를 예약-Service 에 묻는다. 여기서는 그 검사가 목적이 아니다.
    @MockBean
    private RoundClient roundClient;

    @BeforeEach
    void setUp() {
        ownerToken = jwtFor(uniqueUserId(), "ORGANIZER");
        otherToken = jwtFor(uniqueUserId(), "ORGANIZER");

        ResponseEntity<JsonNode> channel = post("/api/v1/channels",
                """
                {"name":"%s","description":"테스트 채널"}
                """.formatted(uniqueName()), ownerToken);
        channelId = channel.getBody().path("data").path("id").asLong();
        assertThat(channelId).as("채널 생성 실패: %s", channel.getBody()).isPositive();

        ResponseEntity<JsonNode> expo = post(expoUrl(), body(), ownerToken);
        expoId = expo.getBody().path("data").path("id").asLong();
        assertThat(expoId).as("박람회 생성 실패: %s", expo.getBody()).isPositive();
    }

    // ---- 주최자용 조회 (수정 화면의 전제) ----

    @Test
    @DisplayName("주최자는 자기 HIDDEN 박람회를 조회할 수 있다")
    void 주최자_비공개_조회() {
        ResponseEntity<JsonNode> response = get(expoUrl() + "/" + expoId, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").path("status").asText()).isEqualTo("HIDDEN");
        assertThat(response.getBody().path("data").path("title").asText()).isEqualTo("테스트 박람회");
    }

    @Test
    @DisplayName("방문자는 HIDDEN 박람회를 여전히 404 로 받는다 - 공개 조회는 그대로 둔다")
    void 방문자_비공개_404() {
        ResponseEntity<JsonNode> response = get("/api/v1/expos/" + expoId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("주최자 목록에는 HIDDEN 박람회도 나온다 - 공개 목록만 보면 등록 직후가 사라진다")
    void 주최자_목록_비공개_포함() {
        ResponseEntity<JsonNode> response = get(expoUrl(), ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data")).anySatisfy(node ->
                assertThat(node.path("id").asLong()).isEqualTo(expoId));
    }

    @Test
    @DisplayName("남의 채널 박람회는 조회도 404 - 403 으로 나누면 존재 여부가 샌다")
    void 타인_조회_404() {
        ResponseEntity<JsonNode> response = get(expoUrl() + "/" + expoId, otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- 수정 ----

    @Test
    @DisplayName("부분 수정 - 보내지 않은 필드는 그대로 두고 updatedAt 만 갱신한다")
    void 부분_수정() {
        ResponseEntity<JsonNode> response = patch(expoUrl() + "/" + expoId,
                """
                {"title":"바뀐 제목"}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = response.getBody().path("data");
        assertThat(data.path("title").asText()).isEqualTo("바뀐 제목");
        assertThat(data.path("venue").asText()).isEqualTo("코엑스");      // 보존
        assertThat(data.path("region").asText()).isEqualTo("서울");       // 보존
        assertThat(data.path("category").asText()).isEqualTo("IT·전자");  // 보존
        assertThat(data.path("updatedAt").asText())
                .isNotEqualTo(data.path("createdAt").asText());
    }

    @Test
    @DisplayName("남의 채널 박람회는 수정할 수 없다 (404)")
    void 타인_수정_404() {
        ResponseEntity<JsonNode> response = patch(expoUrl() + "/" + expoId,
                """
                {"title":"탈취"}
                """, otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("허용되지 않은 category 는 400 - 등록과 같은 제약을 쓴다")
    void 잘못된_카테고리_400() {
        ResponseEntity<JsonNode> response = patch(expoUrl() + "/" + expoId,
                """
                {"category":"아무거나"}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("thumbnailUrl 은 http(s) 로 시작해야 한다 - 화면이 img src 로 쓴다")
    void 잘못된_썸네일_400() {
        ResponseEntity<JsonNode> response = patch(expoUrl() + "/" + expoId,
                """
                {"thumbnailUrl":"javascript:alert(1)"}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("빈 제목은 거절한다 - 부분 수정이라도 필수 필드를 비울 수는 없다")
    void 빈_제목_400() {
        ResponseEntity<JsonNode> response = patch(expoUrl() + "/" + expoId,
                """
                {"title":"   "}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("공개된 박람회도 수정할 수 있고 공개 조회에 즉시 반영된다")
    void 공개_박람회_수정() {
        when(roundClient.existsByExpo(expoId)).thenReturn(true);
        post("/api/v1/expos/" + expoId + "/publication", "{}", ownerToken);

        patch(expoUrl() + "/" + expoId,
                """
                {"description":"공개 후 고친 소개문"}
                """, ownerToken);

        ResponseEntity<JsonNode> publicView = get("/api/v1/expos/" + expoId, null);
        assertThat(publicView.getBody().path("data").path("description").asText())
                .isEqualTo("공개 후 고친 소개문");
    }

    // ---- 상세 이미지 ----

    @Test
    @DisplayName("상세 이미지는 보낸 순서 그대로 저장되고 그대로 내려온다")
    void 상세_이미지_순서_보존() {
        ResponseEntity<JsonNode> response = patch(expoUrl() + "/" + expoId,
                """
                {"detailImageUrls":["https://cdn.test/a.jpg","https://cdn.test/b.jpg","https://cdn.test/c.jpg"]}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode urls = response.getBody().path("data").path("detailImageUrls");
        assertThat(urls).hasSize(3);
        assertThat(urls.get(0).asText()).isEqualTo("https://cdn.test/a.jpg");
        assertThat(urls.get(2).asText()).isEqualTo("https://cdn.test/c.jpg");
    }

    @Test
    @DisplayName("새 배열을 보내면 통째로 교체된다 - 순서 바꾸기도 이 경로 하나로 끝난다")
    void 상세_이미지_교체() {
        patch(expoUrl() + "/" + expoId,
                """
                {"detailImageUrls":["https://cdn.test/a.jpg","https://cdn.test/b.jpg"]}
                """, ownerToken);

        ResponseEntity<JsonNode> response = patch(expoUrl() + "/" + expoId,
                """
                {"detailImageUrls":["https://cdn.test/b.jpg","https://cdn.test/a.jpg"]}
                """, ownerToken);

        JsonNode urls = response.getBody().path("data").path("detailImageUrls");
        assertThat(urls).hasSize(2);
        assertThat(urls.get(0).asText()).isEqualTo("https://cdn.test/b.jpg");
    }

    @Test
    @DisplayName("빈 배열은 전부 지우기, 필드를 안 보내면 그대로 둔다")
    void 상세_이미지_비우기와_유지() {
        patch(expoUrl() + "/" + expoId,
                """
                {"detailImageUrls":["https://cdn.test/a.jpg"]}
                """, ownerToken);

        // 다른 필드만 수정 - 이미지는 건드리지 않는다
        ResponseEntity<JsonNode> kept = patch(expoUrl() + "/" + expoId,
                """
                {"title":"제목만 수정"}
                """, ownerToken);
        assertThat(kept.getBody().path("data").path("detailImageUrls")).hasSize(1);

        ResponseEntity<JsonNode> cleared = patch(expoUrl() + "/" + expoId,
                """
                {"detailImageUrls":[]}
                """, ownerToken);
        assertThat(cleared.getBody().path("data").path("detailImageUrls")).isEmpty();
    }

    @Test
    @DisplayName("http(s) 가 아닌 이미지 주소는 400")
    void 상세_이미지_형식_400() {
        ResponseEntity<JsonNode> response = patch(expoUrl() + "/" + expoId,
                """
                {"detailImageUrls":["javascript:alert(1)"]}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String expoUrl() {
        return "/api/v1/channels/" + channelId + "/expos";
    }

    private String body() {
        return """
                {
                  "title": "테스트 박람회",
                  "category": "IT·전자",
                  "description": "설명",
                  "venue": "코엑스",
                  "region": "서울"
                }
                """;
    }
}
