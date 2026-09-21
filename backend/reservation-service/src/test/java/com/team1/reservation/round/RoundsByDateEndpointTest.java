package com.team1.reservation.round;

import com.team1.reservation.round.controller.InternalRoundController;
import com.team1.reservation.round.dto.InternalRoundResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /internal/v1/rounds/by-date 의 HTTP 계층 검증.
 *
 * <p>서비스 단위 테스트({@link RoundsByDateTest})는 컨트롤러를 안 타서, {@code @RequestParam} 애노테이션
 * 자체가 잘못돼(파라미터 이름이 엉뚱하게 묶여) 모든 요청이 400 나는 버그를 못 잡았다 - 상민님이 지적한 부분.
 */
@WebMvcTest(controllers = InternalRoundController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "internal.token=test-internal-token"
})
class RoundsByDateEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoundService roundService;

    @Test
    @DisplayName("bookableOnly 를 안 보내도 400 이 아니라 기본값 false 로 처리된다")
    void bookableOnlyDefaultsToFalseWhenOmitted() throws Exception {
        when(roundService.roundsByDate(any(), any(), any(), eq(false))).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/rounds/by-date")
                        .param("from", "2026-09-19T00:00:00Z")
                        .param("to", "2026-09-19T23:59:59Z")
                        .param("expoIds", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("bookableOnly=true 를 명시하면 서비스에 true 로 전달된다")
    void passesBookableOnlyTrue() throws Exception {
        when(roundService.roundsByDate(List.of(1L), Instant.parse("2026-09-19T00:00:00Z"),
                Instant.parse("2026-09-19T23:59:59Z"), true)).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/rounds/by-date")
                        .param("from", "2026-09-19T00:00:00Z")
                        .param("to", "2026-09-19T23:59:59Z")
                        .param("expoIds", "1")
                        .param("bookableOnly", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Envelope 없이 raw 배열로 반환한다")
    void returnsRawArray() throws Exception {
        InternalRoundResponse round = new InternalRoundResponse(68L, 12L, 1,
                Instant.parse("2026-09-19T05:00:00Z"), Instant.parse("2026-09-19T09:00:00Z"),
                200, 87, 15000);
        when(roundService.roundsByDate(any(), any(), any(), eq(false))).thenReturn(List.of(round));

        mockMvc.perform(get("/internal/v1/rounds/by-date")
                        .param("from", "2026-09-19T00:00:00Z")
                        .param("to", "2026-09-19T23:59:59Z")
                        .param("expoIds", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roundId").value(68))
                .andExpect(jsonPath("$[0].expoId").value(12))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    @DisplayName("expoIds 가 없으면 400 INVALID_REQUEST 로 거절한다")
    void rejectsMissingExpoIds() throws Exception {
        mockMvc.perform(get("/internal/v1/rounds/by-date")
                        .param("from", "2026-09-19T00:00:00Z")
                        .param("to", "2026-09-19T23:59:59Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }
}
