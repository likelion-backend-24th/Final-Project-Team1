package com.team1.identity.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CrossCuttingFixture가 실제로 독립적으로 동작하는지 확인한다
 * (#18 완료 조건: "Fixture가 독립 실행된다"). 순서에 의존하지 않고,
 * 몇 번을 반복 호출해도 서로 다른 계정이 준비된다.
 */
class CrossCuttingFixtureTest extends ApiTestSupport {

    @Test
    @DisplayName("관리자 토큰으로 발급을 호출하면 성공한다")
    void 관리자_토큰_동작() {
        String adminToken = CrossCuttingFixture.adminToken(this);

        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers",
                AuthFixture.createOrganizerBody(AuthFixture.newEmail()), adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("주최자 토큰을 두 번 만들면 서로 다른 계정이 준비된다")
    void 주최자_토큰_두명_독립() {
        String adminToken = CrossCuttingFixture.adminToken(this);

        String organizer1 = CrossCuttingFixture.organizerToken(this, adminToken);
        String organizer2 = CrossCuttingFixture.organizerToken(this, adminToken);

        assertThat(organizer1).isNotEqualTo(organizer2);
    }

    @Test
    @DisplayName("회원 토큰은 관리자 전용 기능에서 403으로 거절된다")
    void 회원_토큰_동작() {
        String memberToken = CrossCuttingFixture.memberToken(this);

        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers",
                AuthFixture.createOrganizerBody(AuthFixture.newEmail()), memberToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
