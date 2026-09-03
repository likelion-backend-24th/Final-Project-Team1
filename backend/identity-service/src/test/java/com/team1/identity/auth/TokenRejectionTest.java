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
 * 서명·만료가 어긋난 Token은 서비스 로직에 닿기 전에 거절돼야 한다.
 * 실제 Servlet 필터 체인을 태우기 위해 HTTP로 호출한다.
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
    @DisplayName("Token 문자열이 망가져 있으면 401이다")
    void 형식이_깨진_토큰() {
        ResponseEntity<JsonNode> response = post("/api/v1/admin/organizers", body(), "not-a-jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
