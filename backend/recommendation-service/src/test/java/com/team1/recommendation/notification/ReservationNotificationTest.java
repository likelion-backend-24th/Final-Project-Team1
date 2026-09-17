package com.team1.recommendation.notification;

import com.team1.recommendation.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 예약 확정 이벤트 → 알림 생성 → 개수·읽음 처리 (#231). */
class ReservationNotificationTest extends ApiTestSupport {

    @Test
    void 예약확정_이벤트를_받으면_알림이_생긴다() throws Exception {
        long userId = 2001L;
        confirmed(userId, 10L, 501L, "R-20260917-0001");

        mockMvc.perform(get("/api/v1/me/notifications")
                        .header("Authorization", "Bearer " + userJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(1))
                .andExpect(jsonPath("$.data.notifications[0].expoId").value(10))
                .andExpect(jsonPath("$.data.notifications[0].type").value("RESERVATION_CONFIRMED"))
                .andExpect(jsonPath("$.data.notifications[0].isRead").value(false))
                .andExpect(jsonPath("$.data.notifications[0].message", containsString("R-20260917-0001")))
                // UTC 로 내려야 브라우저가 시간대를 맞게 바꾼다
                .andExpect(jsonPath("$.data.notifications[0].createdAt", containsString("Z")));

        unreadCountIs(userId, 1);
    }

    @Test
    void 같은_예약이_다시_와도_알림은_한건이다() throws Exception {
        long userId = 2002L;
        confirmed(userId, 10L, 502L, "R-1");
        confirmed(userId, 10L, 502L, "R-1");

        unreadCountIs(userId, 1);
    }

    @Test
    void 같은_박람회라도_예약이_다르면_각각_알림이_생긴다() throws Exception {
        long userId = 2003L;
        confirmed(userId, 10L, 503L, "R-1");
        confirmed(userId, 10L, 504L, "R-2");

        unreadCountIs(userId, 2);
    }

    @Test
    void 예약ID가_없는_이벤트나_체크인은_알림을_만들지_않는다() throws Exception {
        long userId = 2004L;
        event("""
                {"userId": %d, "expoId": 10, "eventType": "RESERVATION_CONFIRMED"}
                """.formatted(userId));
        event("""
                {"userId": %d, "expoId": 10, "eventType": "CHECKED_IN", "reservationId": 505}
                """.formatted(userId));

        unreadCountIs(userId, 0);
    }

    @Test
    void 모두읽음은_내_알림만_읽음으로_바꾼다() throws Exception {
        long me = 2005L;
        long other = 2006L;
        confirmed(me, 10L, 506L, "R-1");
        confirmed(me, 11L, 507L, "R-2");
        confirmed(other, 10L, 508L, "R-3");

        mockMvc.perform(patch("/api/v1/me/notifications/read-all")
                        .header("Authorization", "Bearer " + userJwt(me)))
                .andExpect(status().isOk());

        unreadCountIs(me, 0);
        unreadCountIs(other, 1);
        mockMvc.perform(get("/api/v1/me/notifications?unreadOnly=true")
                        .header("Authorization", "Bearer " + userJwt(me)))
                .andExpect(jsonPath("$.data.notifications.length()").value(0));
    }

    @Test
    void 남의_알림은_읽음처리할_수_없다() throws Exception {
        long owner = 2007L;
        confirmed(owner, 10L, 509L, "R-1");
        String body = mockMvc.perform(get("/api/v1/me/notifications")
                        .header("Authorization", "Bearer " + userJwt(owner)))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"notifications\":\\[\\{\"id\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/api/v1/me/notifications/" + id + "/read")
                        .header("Authorization", "Bearer " + userJwt(2008L)))
                .andExpect(status().isForbidden());
        unreadCountIs(owner, 1);
    }

    @Test
    void 안읽은개수_인증없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/me/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    private void confirmed(long userId, long expoId, long reservationId, String reservationNo) throws Exception {
        event("""
                {"userId": %d, "expoId": %d, "eventType": "RESERVATION_CONFIRMED",
                 "reservationId": %d, "reservationNo": "%s"}
                """.formatted(userId, expoId, reservationId, reservationNo));
    }

    private void event(String json) throws Exception {
        mockMvc.perform(post("/internal/v1/recommendations/events")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private void unreadCountIs(long userId, long expected) throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications/unread-count")
                        .header("Authorization", "Bearer " + userJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(expected));
    }
}
