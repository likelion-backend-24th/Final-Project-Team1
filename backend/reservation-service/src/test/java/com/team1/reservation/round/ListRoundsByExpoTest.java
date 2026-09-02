package com.team1.reservation.round;

import com.team1.reservation.round.controller.InternalRoundController;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.service.RoundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = InternalRoundController.class)
@AutoConfigureMockMvc(addFilters = false)   // 내부 Token Filter 는 별도 Test 에서 검증한다
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "internal.token=test-internal-token"
})
class ListRoundsByExpoTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoundService roundService;

    private Round round(String startsAt, String endsAt, int capacity) {
        return Round.create(1L, Instant.parse(startsAt), Instant.parse(endsAt), capacity, 10000, NOW);
    }

    @Test
    @DisplayName("계약대로 Envelope 없이 회차 배열을 반환한다")
    void returnsRoundsAsRawArray() throws Exception {
        when(roundService.listByExpo(1L)).thenReturn(List.of(round(
                "2026-09-10T02:00:00Z", "2026-09-10T08:00:00Z", 100)));

        mockMvc.perform(get("/internal/v1/rounds").param("expoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].startsAt").value("2026-09-10T02:00:00Z"))
                .andExpect(jsonPath("$[0].endsAt").value("2026-09-10T08:00:00Z"))
                .andExpect(jsonPath("$[0].capacity").value(100))
                .andExpect(jsonPath("$[0].remaining").value(100))
                // 성공 Envelope 로 감싸지 않는다
                .andExpect(jsonPath("$.success").doesNotExist())
                // 계약에 없는 필드는 내보내지 않는다
                .andExpect(jsonPath("$[0].fee").doesNotExist());
    }

    @Test
    @DisplayName("회차가 없는 박람회는 빈 배열을 반환한다 (404 가 아니다)")
    void returnsEmptyArrayWhenNoRounds() throws Exception {
        when(roundService.listByExpo(99L)).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/rounds").param("expoId", "99"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("Service 가 준 순서(starts_at 오름차순)를 그대로 유지한다")
    void preservesOrder() throws Exception {
        when(roundService.listByExpo(1L)).thenReturn(List.of(
                round("2026-09-10T02:00:00Z", "2026-09-10T08:00:00Z", 10),
                round("2026-09-11T02:00:00Z", "2026-09-11T08:00:00Z", 20),
                round("2026-09-12T02:00:00Z", "2026-09-12T08:00:00Z", 30)));

        mockMvc.perform(get("/internal/v1/rounds").param("expoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].capacity").value(10))
                .andExpect(jsonPath("$[1].capacity").value(20))
                .andExpect(jsonPath("$[2].capacity").value(30));
    }

    @Test
    @DisplayName("expoId 가 없으면 400 INVALID_REQUEST 로 거절한다")
    void rejectsMissingExpoId() throws Exception {
        mockMvc.perform(get("/internal/v1/rounds"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }
}
