package com.team1.expo.expo;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.expo.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CreateExpoServiceTest extends ApiTestSupport {

    private long channelId;
    private final long ownerId = 100L;
    private final String ownerToken = jwtFor(ownerId, "ORGANIZER");

    @BeforeEach
    void createChannel() {
        ResponseEntity<JsonNode> res = post("/api/v1/channels",
                """
                {"name":"%s","description":"테스트 채널"}
                """.formatted(uniqueName()), ownerToken);
        channelId = res.getBody().path("data").path("id").asLong();
    }

    @Test
    @DisplayName("ORGANIZER가 자기 채널에 박람회를 등록하면 201과 expoId를 반환한다")
    void 박람회_등록_성공() {
        ResponseEntity<JsonNode> response = post(expoUrl(channelId), body(), ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("id").asLong()).isPositive();
        assertThat(response.getBody().path("data").path("status").asText()).isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("다른 주최자의 채널에 박람회를 등록하면 403이 반환된다")
    void 타인_채널_등록_거절() {
        String otherToken = jwtFor(999L, "ORGANIZER");

        ResponseEntity<JsonNode> response = post(expoUrl(channelId), body(), otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 채널 ID로 등록하면 403이 반환된다")
    void 없는_채널_등록_거절() {
        ResponseEntity<JsonNode> response = post(expoUrl(99999L), body(), ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("JWT 없이 요청하면 401이 반환된다")
    void 인증_없이_거절() {
        ResponseEntity<JsonNode> response = post(expoUrl(channelId), body(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("USER role이 요청하면 403이 반환된다")
    void USER_역할_거절() {
        String userToken = jwtFor(ownerId, "USER");

        ResponseEntity<JsonNode> response = post(expoUrl(channelId), body(), userToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("허용되지 않는 카테고리로 등록하면 400이 반환된다")
    void 잘못된_카테고리_거절() {
        ResponseEntity<JsonNode> response = post(expoUrl(channelId),
                """
                {"title":"테스트","category":"잘못된카테고리"}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("필수 필드 누락 시 400이 반환된다")
    void 필수_필드_누락_거절() {
        ResponseEntity<JsonNode> response = post(expoUrl(channelId),
                """
                {"title":"제목만있음"}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String expoUrl(long channelId) {
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
