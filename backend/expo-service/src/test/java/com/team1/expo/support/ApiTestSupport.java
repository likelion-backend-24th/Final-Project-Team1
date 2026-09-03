package com.team1.expo.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

public abstract class ApiTestSupport extends IntegrationTestSupport {

    @Autowired
    protected TestRestTemplate restTemplate;

    protected ResponseEntity<JsonNode> post(String path, String body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    protected String errorCode(ResponseEntity<JsonNode> response) {
        return response.getBody().path("data").path("code").asText();
    }
}
