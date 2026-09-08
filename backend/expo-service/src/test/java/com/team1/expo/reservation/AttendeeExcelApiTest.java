package com.team1.expo.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.expo.client.ReservationClient.AttendeeItem;
import com.team1.expo.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class AttendeeExcelApiTest extends ApiTestSupport {

    private long ownerId;
    private String ownerToken;
    private long expoId;

    @BeforeEach
    void setUp() {
        ownerId = uniqueUserId();
        ownerToken = jwtFor(ownerId, "ORGANIZER");

        ResponseEntity<JsonNode> channelRes = post("/api/v1/channels",
                """
                {"name":"%s","description":"엑셀 테스트"}
                """.formatted(uniqueName()), ownerToken);
        long channelId = channelRes.getBody().path("data").path("id").asLong();

        ResponseEntity<JsonNode> expoRes = post("/api/v1/channels/" + channelId + "/expos",
                """
                {"title":"엑셀 박람회","category":"IT·전자","description":"설명","venue":"코엑스","region":"서울"}
                """, ownerToken);
        expoId = expoRes.getBody().path("data").path("id").asLong();
    }

    @Test
    @DisplayName("참석자 목록을 xlsx로 다운로드한다")
    void 엑셀_다운로드_성공() {
        when(reservationClient.getAttendees(expoId, null)).thenReturn(List.of(
                new AttendeeItem(1L, "R-001", 1L, "홍길동", "010-1234-5678", 2, 0, "CONFIRMED", "2026-09-01T10:00:00")
        ));

        ResponseEntity<byte[]> response = getBytes("/api/v1/expos/" + expoId + "/reservations/attendees.xlsx", ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("spreadsheetml.sheet");
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    @DisplayName("회차 필터를 적용해 다운로드한다")
    void 회차_필터_엑셀_다운로드() {
        when(reservationClient.getAttendees(expoId, 1L)).thenReturn(List.of(
                new AttendeeItem(1L, "R-001", 1L, "홍길동", "010-0000-0000", 1, 0, "CONFIRMED", "2026-09-01T10:00:00")
        ));

        ResponseEntity<byte[]> response = getBytes("/api/v1/expos/" + expoId + "/reservations/attendees.xlsx?roundId=1", ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    @DisplayName("타인 박람회 다운로드 시 403")
    void 타인_박람회_접근_거절() {
        String otherToken = jwtFor(uniqueUserId(), "ORGANIZER");

        ResponseEntity<JsonNode> response = get("/api/v1/expos/" + expoId + "/reservations/attendees.xlsx", otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("JWT 없으면 401")
    void 인증_없이_접근_거절() {
        ResponseEntity<JsonNode> response = get("/api/v1/expos/" + expoId + "/reservations/attendees.xlsx", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
