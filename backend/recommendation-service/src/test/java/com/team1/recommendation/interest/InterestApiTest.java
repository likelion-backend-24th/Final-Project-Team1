package com.team1.recommendation.interest;

import com.team1.recommendation.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InterestApiTest extends ApiTestSupport {

    @Test
    void upsertInterests_성공() throws Exception {
        mockMvc.perform(put("/api/v1/me/interests")
                        .header("Authorization", "Bearer " + USER_JWT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categories": ["IT", "패션"],
                                  "keywords": ["AI", "스타트업"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categories[0]").value("IT"))
                .andExpect(jsonPath("$.data.keywords[0]").value("AI"));
    }

    @Test
    void upsertInterests_인증없으면_401() throws Exception {
        mockMvc.perform(put("/api/v1/me/interests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories": ["IT"], "keywords": []}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
