package com.team1.reservation.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.round.controller.RoundController;
import com.team1.reservation.round.dto.CreateRoundRequest;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.service.RoundService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = RoundController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "internal.token=test-internal-token"
})
class RoundControllerTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(7L, "ORGANIZER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoundService roundService;

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private String body(Integer capacity) throws Exception {
        return objectMapper.writeValueAsString(new CreateRoundRequest(
                Instant.parse("2026-09-10T02:00:00Z"),
                Instant.parse("2026-09-10T08:00:00Z"),
                capacity,
                0));
    }

    @Test
    @DisplayName("정상 등록은 201 과 성공 Envelope 를 반환한다")
    void createsRound() throws Exception {
        AuthContext.set(OWNER);
        Round saved = Round.create(1L,
                Instant.parse("2026-09-10T02:00:00Z"),
                Instant.parse("2026-09-10T08:00:00Z"),
                100, 0, Instant.parse("2026-09-02T00:00:00Z"));
        when(roundService.create(eq(1L), any(), any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/expos/{expoId}/rounds", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(100)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.capacity").value(100))
                .andExpect(jsonPath("$.data.remaining").value(100));
    }

    @Test
    @DisplayName("정원이 1 미만이면 Bean Validation 이 400 INVALID_REQUEST 로 막는다")
    void rejectsCapacityBelowOne() throws Exception {
        AuthContext.set(OWNER);

        mockMvc.perform(post("/api/v1/expos/{expoId}/rounds", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("다른 주최자의 박람회면 403 FORBIDDEN 을 반환한다")
    void returnsForbidden() throws Exception {
        AuthContext.set(OWNER);
        when(roundService.create(eq(1L), any(), any()))
                .thenThrow(new ApiException(ErrorCode.FORBIDDEN, "not the owner"));

        mockMvc.perform(post("/api/v1/expos/{expoId}/rounds", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("박람회 조회 실패는 503 DEPENDENCY_UNAVAILABLE 과 Trace ID 를 반환한다")
    void returnsServiceUnavailable() throws Exception {
        AuthContext.set(OWNER);
        when(roundService.create(eq(1L), any(), any()))
                .thenThrow(new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "expo-service unavailable"));

        mockMvc.perform(post("/api/v1/expos/{expoId}/rounds", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(10)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.meta.traceId").exists());
    }

    @Test
    @DisplayName("인증 주체가 없으면 401 과 WWW-Authenticate 헤더를 반환한다")
    void returnsUnauthenticated() throws Exception {
        // AuthContext 를 채우지 않는다 = Token 없이 들어온 요청
        mockMvc.perform(post("/api/v1/expos/{expoId}/rounds", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(10)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.data.code").value("UNAUTHENTICATED"));
    }
}
