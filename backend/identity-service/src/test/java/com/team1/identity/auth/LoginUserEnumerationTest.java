package com.team1.identity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.identity.support.ApiTestSupport;
import com.team1.identity.support.AuthFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 실패는 "가입되지 않은 이메일"과 "틀린 비밀번호"를 구분할 수 없어야 한다.
 *
 * 응답 본문만 같게 맞추는 것으로는 부족하다. 사용자를 못 찾았을 때 Hash 비교를 건너뛰면
 * BCrypt(work factor 12) 한 번에 해당하는 시간만큼 응답이 빨라지므로, 시간만 재도
 * 그 이메일이 가입돼 있는지 알 수 있다(User Enumeration).
 */
class LoginUserEnumerationTest extends ApiTestSupport {

    /*
     * BCrypt work factor 12는 보통 200ms 이상 걸린다. Hash 비교를 건너뛰면 한 자리 ms로 끝난다.
     * 실행 환경에 따라 편차가 있으므로 넉넉히 낮춘 값으로 "비교를 수행했는지"만 판별한다.
     */
    private static final long BCRYPT_MIN_MILLIS = 50;

    @Test
    @DisplayName("가입되지 않은 이메일과 틀린 비밀번호는 응답도 소요 시간도 구분되지 않는다")
    void 가입_여부가_드러나지_않는다() {
        String registered = AuthFixture.newEmail();
        post("/api/v1/auth/signup", AuthFixture.signUpBody(registered));

        // JIT·Connection Pool 예열 — 첫 호출의 초기화 비용이 측정에 섞이지 않게 한다
        post("/api/v1/auth/login", AuthFixture.loginBody(registered, AuthFixture.WRONG_PASSWORD));

        long beforeUnknown = System.nanoTime();
        ResponseEntity<JsonNode> unknownEmail = post("/api/v1/auth/login",
                AuthFixture.loginBody(AuthFixture.newEmail(), AuthFixture.VALID_PASSWORD));
        long unknownMillis = (System.nanoTime() - beforeUnknown) / 1_000_000;

        ResponseEntity<JsonNode> wrongPassword = post("/api/v1/auth/login",
                AuthFixture.loginBody(registered, AuthFixture.WRONG_PASSWORD));

        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(unknownEmail.getStatusCode());
        assertThat(errorCode(unknownEmail)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(errorCode(wrongPassword)).isEqualTo(errorCode(unknownEmail));
        assertThat(unknownEmail.getBody().path("message").asText())
                .isEqualTo(wrongPassword.getBody().path("message").asText());

        assertThat(unknownMillis)
                .as("가입되지 않은 이메일이어도 Hash 비교를 수행해야 한다 — %dms 만에 끝났다면 건너뛴 것",
                        unknownMillis)
                .isGreaterThanOrEqualTo(BCRYPT_MIN_MILLIS);
    }
}
