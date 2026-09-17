package com.team1.recommendation.internal;

import com.team1.recommendation.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalApiTest extends ApiTestSupport {

    @Test
    void expoPublished_성공() throws Exception {
        mockMvc.perform(post("/internal/v1/recommendations/expo-published")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expoId": 1,
                                  "title": "2026 서울 AI 박람회",
                                  "description": "AI 스타트업 중심의 네트워킹 박람회"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void expoPublished_토큰없으면_401() throws Exception {
        mockMvc.perform(post("/internal/v1/recommendations/expo-published")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expoId": 1, "title": "t", "description": "d"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void behaviorEvent_예약확정_성공() throws Exception {
        mockMvc.perform(post("/internal/v1/recommendations/events")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 42,
                                  "expoId": 1,
                                  "eventType": "RESERVATION_CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void behaviorEvent_체크인_성공() throws Exception {
        mockMvc.perform(post("/internal/v1/recommendations/events")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 42,
                                  "expoId": 1,
                                  "eventType": "CHECKED_IN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void behaviorEvent_토큰없으면_401() throws Exception {
        mockMvc.perform(post("/internal/v1/recommendations/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 1, "expoId": 1, "eventType": "CHECKED_IN"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
