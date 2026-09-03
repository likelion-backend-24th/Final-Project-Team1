package com.team1.expo.channel;

import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CreateChannelServiceTest extends ApiTestSupport {

    @Autowired
    private ChannelRepository channelRepository;

    @Test
    @DisplayName("ORGANIZER가 채널을 생성하면 201과 채널 ID를 반환한다")
    void 채널_생성_성공() {
        String token = jwtFor(1L, "ORGANIZER");
        String name = uniqueName();

        ResponseEntity<JsonNode> response = post("/api/v1/channels",
                """
                {"name":"%s","description":"설명"}
                """.formatted(name), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("id").asLong()).isPositive();
        assertThat(response.getBody().path("data").path("name").asText()).isEqualTo(name);
    }

    @Test
    @DisplayName("같은 이름으로 두 번 생성하면 409 DUPLICATE_CHANNEL_NAME이 반환된다")
    void 중복_채널명_거절() {
        String token = jwtFor(1L, "ORGANIZER");
        String name = uniqueName();

        post("/api/v1/channels", """
                {"name":"%s","description":"첫번째"}
                """.formatted(name), token);

        ResponseEntity<JsonNode> response = post("/api/v1/channels",
                """
                {"name":"%s","description":"두번째"}
                """.formatted(name), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(response)).isEqualTo("DUPLICATE_CHANNEL_NAME");
    }

    @Test
    @DisplayName("JWT 없이 채널을 생성하면 401이 반환된다")
    void 인증_없이_거절() {
        ResponseEntity<JsonNode> response = post("/api/v1/channels",
                """
                {"name":"%s"}
                """.formatted(uniqueName()), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("USER role이 채널을 생성하면 403이 반환된다")
    void USER_역할_거절() {
        String token = jwtFor(2L, "USER");

        ResponseEntity<JsonNode> response = post("/api/v1/channels",
                """
                {"name":"%s"}
                """.formatted(uniqueName()), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("중복 채널명 실패 시 DB 채널 건수가 늘어나지 않는다")
    void 중복_채널명_실패_시_DB_건수_불변() {
        String token = jwtFor(1L, "ORGANIZER");
        String name = uniqueName();

        post("/api/v1/channels", """
                {"name":"%s","description":"첫번째"}
                """.formatted(name), token);

        long before = channelRepository.count();
        post("/api/v1/channels", """
                {"name":"%s","description":"두번째"}
                """.formatted(name), token);
        long after = channelRepository.count();

        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("권한 없는 요청 실패 시 DB 채널 건수가 늘어나지 않는다")
    void 권한_없는_요청_실패_시_DB_건수_불변() {
        String userToken = jwtFor(3L, "USER");

        long before = channelRepository.count();
        post("/api/v1/channels", """
                {"name":"%s"}
                """.formatted(uniqueName()), userToken);
        long after = channelRepository.count();

        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("채널 이름이 비어있으면 400이 반환된다")
    void 빈_이름_거절() {
        String token = jwtFor(1L, "ORGANIZER");

        ResponseEntity<JsonNode> response = post("/api/v1/channels",
                """
                {"name":""}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
