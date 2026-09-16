package com.team1.expo.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public abstract class ApiTestSupport extends IntegrationTestSupport {

    @Autowired
    protected TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

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

    protected ResponseEntity<byte[]> getBytes(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    /**
     * TestRestTemplate 의 기본 요청 팩터리는 PATCH 를 지원하지 않는다(SimpleClientHttpRequestFactory).
     * 다른 Test 에 영향을 주지 않도록 여기서만 JDK HttpClient 기반 템플릿을 따로 만든다.
     */
    protected ResponseEntity<JsonNode> patch(String path, String body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory());
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;   // 4xx 도 예외 없이 그대로 받는다
            }
        });
        return template.exchange("http://localhost:" + port + path,
                HttpMethod.PATCH, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected String errorCode(ResponseEntity<JsonNode> response) {
        return response.getBody().path("data").path("code").asText();
    }
}
