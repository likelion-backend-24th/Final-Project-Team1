package com.team1.identity.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 실제 Servlet 필터 체인(common-security의 JwtAuthenticationFilter 포함)을 그대로 태우기 위해
 * MockMvc가 아니라 RANDOM_PORT + TestRestTemplate으로 호출한다.
 */
public abstract class ApiTestSupport extends IntegrationTestSupport {

    @Autowired
    protected TestRestTemplate restTemplate;

    protected ResponseEntity<JsonNode> post(String path, String body) {
        return post(path, body, null);
    }

    protected ResponseEntity<JsonNode> post(String path, String body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected String loginAndGetToken(String email, String password) {
        ResponseEntity<JsonNode> response = post("/api/v1/auth/login",
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
        return response.getBody().path("data").path("accessToken").asText();
    }

    protected String errorCode(ResponseEntity<JsonNode> response) {
        return response.getBody().path("data").path("code").asText();
    }
}
