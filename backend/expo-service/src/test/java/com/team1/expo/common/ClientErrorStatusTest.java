package com.team1.expo.common;

import com.team1.expo.expo.controller.ExpoController;
import com.team1.expo.expo.controller.ExpoQueryController;
import com.team1.expo.expo.service.ExpoQueryService;
import com.team1.expo.expo.service.ExpoService;
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
 * Spring MVC가 던지는 클라이언트 오류가 500으로 뭉개지지 않는지 확인한다.
 *
 * GlobalExceptionHandler의 @ExceptionHandler(Exception.class)가 이 예외들까지
 * 삼키면, 클라이언트는 "내 요청이 잘못됨"과 "서버 장애"를 구분할 수 없고
 * 오타 URL 한 건마다 Stack Trace가 error Log에 쌓인다.
 */
@WebMvcTest(controllers = {ExpoQueryController.class, ExpoController.class})
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "internal.token=test-internal-token"
})
class ClientErrorStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpoQueryService expoQueryService;

    @MockitoBean
    private ExpoService expoService;

    @Test
    @DisplayName("존재하지 않는 경로는 500이 아니라 404와 NOT_FOUND를 반환한다")
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("허용되지 않은 메서드는 500이 아니라 405를 반환한다")
    void methodNotAllowedKeepsItsStatus() throws Exception {
        mockMvc.perform(delete("/api/v1/expos"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 500이 아니라 415를 반환한다")
    void unsupportedMediaTypeKeepsItsStatus() throws Exception {
        mockMvc.perform(post("/api/v1/channels/1/expos")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }
}
