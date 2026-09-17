package com.team1.recommendation.notification;

import com.team1.recommendation.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationApiTest extends ApiTestSupport {

    @Test
    void 알림목록_인증없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 알림목록_인증있으면_빈목록_200() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications")
                        .header("Authorization", "Bearer " + USER_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.notifications").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 읽음처리_없는ID_404() throws Exception {
        mockMvc.perform(patch("/api/v1/me/notifications/99999/read")
                        .header("Authorization", "Bearer " + USER_JWT))
                .andExpect(status().isNotFound());
    }

    @Test
    void 읽음처리_인증없으면_401() throws Exception {
        mockMvc.perform(patch("/api/v1/me/notifications/1/read"))
                .andExpect(status().isUnauthorized());
    }
}
