package com.team1.identity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.identity.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MemberSignUpAndLoginE2ETest extends ApiTestSupport {

    @Test
    @DisplayName("가입 → 로그인 → 발급받은 토큰으로 보호 기능 호출까지 이어진다")
    void 가입부터_보호_기능_호출까지() {
        String email = uniqueEmail();

        ResponseEntity<JsonNode> signUp = post("/api/v1/auth/signup", """
                {"email":"%s","password":"password123","name":"정선우"}
                """.formatted(email));

        assertThat(signUp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signUp.getBody().path("success").asBoolean()).isTrue();
        assertThat(signUp.getBody().path("data").path("name").asText()).isEqualTo("정선우");

        ResponseEntity<JsonNode> login = post("/api/v1/auth/login", """
                {"email":"%s","password":"password123"}
                """.formatted(email));

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login.getBody().path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();

        // 토큰은 정상 인식되지만 USER 권한이므로 403. 401이 아니라는 점이 "인증은 됐다"는 증거다.
        ResponseEntity<JsonNode> protectedCall = post("/api/v1/admin/organizers", """
                {"email":"%s","password":"password123","name":"주최자"}
                """.formatted(uniqueEmail()), token);

        assertThat(protectedCall.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400 INVALID_REQUEST")
    void 이메일_형식_위반() {
        ResponseEntity<JsonNode> response = post("/api/v1/auth/signup", """
                {"email":"not-an-email","password":"password123","name":"테스터"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(response)).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("비밀번호 정책을 어기면 400 INVALID_REQUEST")
    void 비밀번호_정책_위반() {
        ResponseEntity<JsonNode> response = post("/api/v1/auth/signup", """
                {"email":"%s","password":"short","name":"테스터"}
                """.formatted(uniqueEmail()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(response)).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409 DUPLICATE_EMAIL")
    void 이메일_중복() {
        String email = uniqueEmail();
        String body = """
                {"email":"%s","password":"password123","name":"테스터"}
                """.formatted(email);

        post("/api/v1/auth/signup", body);
        ResponseEntity<JsonNode> second = post("/api/v1/auth/signup", body);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(second)).isEqualTo("DUPLICATE_EMAIL");
    }
}
