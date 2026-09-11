package com.team1.identity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.identity.support.ApiTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서명·만료가 어긋난 Token으로는 보호 기능을 쓸 수 없어야 한다. 다만 필터가 직접 끊지는 않고,
 * 헤더가 없을 때와 같이 통과시킨 뒤 각 기능의 인증 검사가 거절한다 — 그래야 만료 Token 하나로
 * 로그인 같은 공개 기능까지 막히지 않는다. 실제 Servlet 필터 체인을 태우기 위해 HTTP로 호출한다.
 */
class TokenRejectionTest extends ApiTestSupport {

    private static final String OTHER_SECRET =
            "another-secret-fedcba9876543210fedcba9876543210fedcba98765432";

    @Test
    @DisplayName("만료된 Token으로 보호 기능을 호출하면 401이고 사용자가 생성되지 않는다")
    void 만료된_토큰() {
        String expired = token(TEST_JWT_SECRET,
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));

        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers", body(), expired);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHENTICATED");
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("다른 Secret으로 서명한 Token으로 호출하면 401이다")
    void 서명이_다른_토큰() {
        String forged = token(OTHER_SECRET,
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS));

        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers", body(), forged);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("Token 문자열이 망가져 있으면 401이고 한글 메시지가 깨지지 않는다")
    void 형식이_깨진_토큰() {
        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers", body(), "not-a-jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHENTICATED");
        assertThat(response.getBody().path("message").asText()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    @DisplayName("무효한 Token이 붙어 있어도 인증이 필요 없는 기능은 막히지 않는다")
    void 무효한_토큰은_인증이_필요_없는_요청을_막지_않는다() {
        // 필터가 여기서 401로 끊으면, 브라우저에 남아있는 만료 Token 하나로 로그인조차 못 한다.
        // 통과했다면 서비스까지 닿아 자격증명 오류(INVALID_CREDENTIALS)로 갈린다.
        String expired = token(TEST_JWT_SECRET,
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));

        ResponseEntity<JsonNode> response = post("/api/v1/auth/login",
                """
                {"email":"nobody@team1.local","password":"wrong-password"}
                """, expired);

        assertThat(errorCode(response)).isEqualTo("INVALID_CREDENTIALS");
    }

    private String token(String secret, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject("999")
                .claim("userId", 999L)
                .claim("role", "SUPER_ADMIN")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String body() {
        return """
                {"email":"%s","password":"password123","name":"주최자"}
                """.formatted(uniqueEmail());
    }
}
