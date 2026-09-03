package com.team1.expo.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.expo.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelIsolationTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("다른 주최자의 채널을 조회하면 403이 반환된다")
    void 타인_채널_조회_거절() {
        String ownerToken = jwtFor(10L, "ORGANIZER");
        String otherToken = jwtFor(20L, "ORGANIZER");

        ResponseEntity<JsonNode> created = post("/api/v1/channels",
                """
                {"name":"%s","description":"A의 채널"}
                """.formatted(uniqueName()), ownerToken);

        long channelId = created.getBody().path("data").path("id").asLong();

        ResponseEntity<JsonNode> response = get("/api/v1/channels/" + channelId, otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("존재하지 않는 채널 조회 시 404가 반환된다")
    void 존재하지_않는_채널_404() {
        String token = jwtFor(1L, "ORGANIZER");

        ResponseEntity<JsonNode> response = get("/api/v1/channels/99999999", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("channels 테이블에 identity 스키마를 향한 Foreign Key가 없다")
    void channels_FK_없음() {
        Long fkCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = 'expo'
                  AND TABLE_NAME = 'channels'
                  AND REFERENCED_TABLE_SCHEMA IS NOT NULL
                """,
                Long.class
        );

        assertThat(fkCount).isZero();
    }
}
