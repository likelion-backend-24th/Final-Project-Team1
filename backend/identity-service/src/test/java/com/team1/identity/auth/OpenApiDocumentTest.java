package com.team1.identity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.identity.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI 문서가 실제 구현에서 생성되는지, 세 계약이 모두 포함되는지 확인한다.
 * 경로가 바뀌거나 Controller가 빠지면 여기서 잡힌다.
 */
class OpenApiDocumentTest extends ApiTestSupport {

    @Test
    @DisplayName("OpenAPI 문서에 signUp·login·createOrganizer가 모두 포함된다")
    void 문서에_세_계약이_있다() {
        ResponseEntity<JsonNode> response =
                restTemplate.getForEntity("/v3/api-docs", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode paths = response.getBody().path("paths");
        assertThat(paths.has("/api/v1/auth/signup")).isTrue();
        assertThat(paths.has("/api/v1/auth/login")).isTrue();
        assertThat(paths.has("/api/v1/admin/organizers")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI 문서에 Bearer 인증 스킴이 정의돼 있다")
    void 인증_스킴이_있다() {
        ResponseEntity<JsonNode> response =
                restTemplate.getForEntity("/v3/api-docs", JsonNode.class);

        JsonNode scheme = response.getBody().path("components").path("securitySchemes").path("bearerAuth");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
    }
}
