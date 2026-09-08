package com.team1.reservation.common;

import com.team1.reservation.round.controller.RoundController;
import com.team1.reservation.round.service.RoundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC 가 던지는 Client 오류가 500 으로 뭉개지지 않는지 확인한다.
 *
 * GlobalExceptionHandler 의 @ExceptionHandler(Exception.class) 가 이 예외들까지
 * 삼키면, Client 는 "내 요청이 잘못됨" 과 "서버 장애" 를 구분할 수 없고
 * 오타 URL 한 건마다 Stack Trace 가 error Log 에 쌓인다.
 */
@WebMvcTest(controllers = RoundController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "internal.token=test-internal-token"
})
class ClientErrorStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoundService roundService;

    @Test
    @DisplayName("존재하지 않는 경로는 500 이 아니라 404 와 NOT_FOUND 를 반환한다")
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("허용되지 않은 메서드는 500 이 아니라 405 를 반환한다")
    void methodNotAllowedKeepsItsStatus() throws Exception {
        mockMvc.perform(delete("/api/v1/expos/1/rounds"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type 은 500 이 아니라 415 를 반환한다")
    void unsupportedMediaTypeKeepsItsStatus() throws Exception {
        mockMvc.perform(post("/api/v1/expos/1/rounds")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }
}
