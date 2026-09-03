package com.team1.identity.support;

import java.util.UUID;

/**
 * 가입·인증 Test가 쓰는 요청 본문과 자격 증명을 한곳에 모은다.
 *
 * 모든 Fixture는 서로 독립적이다 — 상태를 공유하지 않고, 이메일은 호출할 때마다
 * 새로 만들며, 실행 순서에 의존하지 않는다. 따라서 어떤 Test든 단독으로 실행할 수 있다.
 */
public final class AuthFixture {

    public static final String VALID_PASSWORD = "password123";
    public static final String WRONG_PASSWORD = "wrongpassword1";

    /** 이메일 형식이 아닌 값 — 400 INVALID_REQUEST를 기대한다 */
    public static final String MALFORMED_EMAIL = "not-an-email";

    /** 비밀번호 정책(8~64자, 영문+숫자) 위반 — 400 INVALID_REQUEST를 기대한다 */
    public static final String POLICY_VIOLATING_PASSWORD = "short";

    private AuthFixture() {
    }

    /** 아직 가입되지 않은 새 이메일 */
    public static String newEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@team1.local";
    }

    public static String signUpBody(String email) {
        return signUpBody(email, VALID_PASSWORD);
    }

    public static String signUpBody(String email, String password) {
        return """
                {"email":"%s","password":"%s","name":"테스터"}
                """.formatted(email, password);
    }

    public static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    public static String createOrganizerBody(String email) {
        return """
                {"email":"%s","password":"%s","name":"주최자"}
                """.formatted(email, VALID_PASSWORD);
    }
}
