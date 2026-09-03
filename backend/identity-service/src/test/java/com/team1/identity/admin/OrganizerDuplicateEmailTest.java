package com.team1.identity.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.identity.support.ApiTestSupport;
import com.team1.identity.support.AuthFixture;
import com.team1.identity.support.CrossCuttingFixture;
import com.team1.identity.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #18(Cross-cutting: 계정 발급·채널 생성·권한 실패 Acceptance Test) 작업 범위
 * "중복 이메일 발급(409)"의 단건(비-동시성) 케이스를 명시적으로 증명한다.
 *
 * ConcurrentCreateOrganizerIntegrationTest는 10개 동시 요청 중 1건만 성공하는지를
 * 증명하지만, "이미 존재하는 이메일로 순차적으로 한 번 더 요청했을 때 정확히
 * 409가 오고 사용자가 추가로 생성되지 않는지"를 확인하는 전용 테스트는 없었다.
 */
class OrganizerDuplicateEmailTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("이미 가입된 이메일로 주최자 발급을 재요청하면 409이고 추가로 생성되지 않는다")
    void 이미_가입된_이메일로_발급하면_409() {
        String adminToken = CrossCuttingFixture.adminToken(this);
        String email = AuthFixture.newEmail();

        ResponseEntity<JsonNode> first = post("/api/v1/admin/organizers", AuthFixture.createOrganizerBody(email), adminToken);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        long totalUsersAfterFirst = userRepository.count();

        ResponseEntity<JsonNode> second = post("/api/v1/admin/organizers", AuthFixture.createOrganizerBody(email), adminToken);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(second)).isEqualTo("DUPLICATE_EMAIL");
        assertThat(userRepository.count()).isEqualTo(totalUsersAfterFirst);
    }
}
