package com.team1.identity.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.identity.support.ApiTestSupport;
import com.team1.identity.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAuthorizationTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("인증 없이 주최자 발급을 호출하면 401이고 사용자가 생성되지 않는다")
    void 인증_없음() {
        String target = uniqueEmail();

        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers", body(target));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHENTICATED");
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(exists(target)).isFalse();
    }

    @Test
    @DisplayName("USER 권한으로 주최자 발급을 호출하면 403이고 사용자가 생성되지 않는다")
    void USER_권한() {
        String memberEmail = uniqueEmail();
        post("/api/v1/auth/signup", """
                {"email":"%s","password":"password123","name":"일반회원"}
                """.formatted(memberEmail));
        String userToken = loginAndGetToken(memberEmail, "password123");

        String target = uniqueEmail();
        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers", body(target), userToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("FORBIDDEN");
        assertThat(exists(target)).isFalse();
    }

    @Test
    @DisplayName("ORGANIZER 권한으로 주최자 발급을 호출해도 403이다")
    void ORGANIZER_권한() {
        String adminToken = loginAndGetToken(SEED_EMAIL, SEED_PASSWORD);
        String organizerEmail = uniqueEmail();
        post("/api/v1/admin/organizers", body(organizerEmail), adminToken);
        String organizerToken = loginAndGetToken(organizerEmail, "password123");

        String target = uniqueEmail();
        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers", body(target), organizerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exists(target)).isFalse();
    }

    @Test
    @DisplayName("SUPER_ADMIN 권한이면 주최자가 ORGANIZER Role로 생성된다")
    void SUPER_ADMIN_권한() {
        String adminToken = loginAndGetToken(SEED_EMAIL, SEED_PASSWORD);
        String target = uniqueEmail();

        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers", body(target), adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("data").path("role").asText()).isEqualTo("ORGANIZER");
        assertThat(exists(target)).isTrue();
    }

    private String body(String email) {
        return """
                {"email":"%s","password":"password123","name":"주최자"}
                """.formatted(email);
    }

    private boolean exists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
