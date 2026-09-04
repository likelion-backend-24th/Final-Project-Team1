package com.team1.identity.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.identity.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 클라이언트 잘못은 4xx로 나가야 한다.
 *
 * GlobalExceptionHandler의 @ExceptionHandler(Exception.class)가 Spring MVC의
 * 클라이언트 오류(경로 없음 404 · 메서드 불일치 405 · Content-Type 불일치 415)까지
 * 삼켜 500으로 바꾸면, 클라이언트가 "내 요청이 잘못됨"과 "서버 장애"를 구분할 수 없고
 * 오타 URL 한 건에도 Stack Trace가 error Log에 쌓인다.
 */
class ErrorStatusMappingTest extends ApiTestSupport {

    @Test
    @DisplayName("없는 경로·잘못된 메서드·잘못된 Content-Type은 4xx여야 한다")
    void 클라이언트_오류는_4xx다() {
        ResponseEntity<JsonNode> unknownPath =
                restTemplate.getForEntity("/api/v1/이런건-없다", JsonNode.class);

        ResponseEntity<JsonNode> wrongMethod = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class);

        HttpHeaders textHeaders = new HttpHeaders();
        textHeaders.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<JsonNode> wrongContentType = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("not json", textHeaders), JsonNode.class);

        assertAll(
                () -> assertThat(unknownPath.getStatusCode().value())
                        .as("없는 경로 → 404 기대").isEqualTo(404),
                () -> assertThat(wrongMethod.getStatusCode().value())
                        .as("GET /login → 405 기대").isEqualTo(405),
                () -> assertThat(wrongContentType.getStatusCode().value())
                        .as("text/plain 본문 → 415 기대").isEqualTo(415)
        );
    }
}
