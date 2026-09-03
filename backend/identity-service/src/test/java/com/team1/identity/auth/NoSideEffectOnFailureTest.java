package com.team1.identity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.identity.support.ApiTestSupport;
import com.team1.identity.support.AuthFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실패한 요청이 데이터를 조금도 바꾸지 않는지 확인한다.
 *
 * "그 사용자가 안 생겼다"가 아니라 users·user_roles 전체 건수를 앞뒤로 비교한다.
 * 엉뚱한 행이 하나라도 생기거나 사라지면 여기서 잡힌다.
 */
class NoSideEffectOnFailureTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("인증 없이 주최자 발급을 호출해도 사용자·Role 건수가 변하지 않는다")
    void 인증_없음() {
        long users = userCount();
        long roles = roleCount();

        ResponseEntity<JsonNode> response =
                post("/api/v1/admin/organizers", AuthFixture.createOrganizerBody(AuthFixture.newEmail()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertNoChange(users, roles);
    }

    @Test
    @DisplayName("USER 권한으로 주최자 발급을 호출해도 사용자·Role 건수가 변하지 않는다")
    void 권한_부족() {
        String member = AuthFixture.newEmail();
        post("/api/v1/auth/signup", AuthFixture.signUpBody(member));
        String userToken = loginAndGetToken(member, AuthFixture.VALID_PASSWORD);

        long users = userCount();
        long roles = roleCount();

        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers",
                AuthFixture.createOrganizerBody(AuthFixture.newEmail()), userToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertNoChange(users, roles);
    }

    @Test
    @DisplayName("이미 가입된 이메일로 다시 가입해도 사용자·Role 건수가 변하지 않는다")
    void 이메일_중복() {
        String email = AuthFixture.newEmail();
        post("/api/v1/auth/signup", AuthFixture.signUpBody(email));

        long users = userCount();
        long roles = roleCount();

        ResponseEntity<JsonNode> response =
                post("/api/v1/auth/signup", AuthFixture.signUpBody(email));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertNoChange(users, roles);
    }

    @Test
    @DisplayName("형식·정책 위반 가입 요청은 사용자·Role 건수를 바꾸지 않는다")
    void 형식과_정책_위반() {
        long users = userCount();
        long roles = roleCount();

        ResponseEntity<JsonNode> malformed = post("/api/v1/auth/signup",
                AuthFixture.signUpBody(AuthFixture.MALFORMED_EMAIL));
        ResponseEntity<JsonNode> weakPassword = post("/api/v1/auth/signup",
                AuthFixture.signUpBody(AuthFixture.newEmail(), AuthFixture.POLICY_VIOLATING_PASSWORD));

        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(weakPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertNoChange(users, roles);
    }

    @Test
    @DisplayName("틀린 비밀번호로 로그인해도 사용자·Role 건수가 변하지 않는다")
    void 로그인_실패() {
        String email = AuthFixture.newEmail();
        post("/api/v1/auth/signup", AuthFixture.signUpBody(email));

        long users = userCount();
        long roles = roleCount();

        ResponseEntity<JsonNode> response =
                post("/api/v1/auth/login", AuthFixture.loginBody(email, AuthFixture.WRONG_PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertNoChange(users, roles);
    }

    private void assertNoChange(long usersBefore, long rolesBefore) {
        assertThat(userCount()).as("users 건수").isEqualTo(usersBefore);
        assertThat(roleCount()).as("user_roles 건수").isEqualTo(rolesBefore);
    }

    private long userCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    private long roleCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_roles", Long.class);
    }
}
