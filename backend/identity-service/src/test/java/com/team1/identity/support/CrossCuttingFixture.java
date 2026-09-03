package com.team1.identity.support;

/**
 * Cross-cutting: 계정 발급·채널 생성·권한 실패 Acceptance Test(#18)가 요구하는
 * "전체관리자 Seed + 주최자 2명 + 회원 1명" 구성을 준비하는 Fixture.
 *
 * 이 Fixture는 identity-service가 소유한 계정·인증 수단만 준비한다. expo-service의
 * 채널 생성은 이 Fixture의 책임이 아니다 — 여기서 발급한 토큰을 그대로
 * expo-service 호출에 재사용하면 된다 (jwt.secret이 서비스 간에 동일하므로
 * common-security가 검증을 통과시킨다).
 *
 * 모든 메서드는 서로 독립적이다 — 상태를 공유하지 않고, 이메일은 호출할 때마다
 * 새로 만들며, 실행 순서에 의존하지 않는다.
 */
public final class CrossCuttingFixture {

    private CrossCuttingFixture() {
    }

    /** SUPER_ADMIN Seed 계정(V2 migration으로 이미 심어진 계정)으로 로그인해 토큰을 받는다 */
    public static String adminToken(ApiTestSupport support) {
        return support.loginAndGetToken(IntegrationTestSupport.SEED_EMAIL, IntegrationTestSupport.SEED_PASSWORD);
    }

    /**
     * 전체관리자 권한으로 새 주최자 계정을 발급하고 로그인해 토큰을 받는다.
     * 두 번 호출하면 서로 다른 이메일의 주최자 2명을 준비할 수 있다.
     */
    public static String organizerToken(ApiTestSupport support, String adminToken) {
        String email = AuthFixture.newEmail();
        support.post("/api/v1/admin/organizers", AuthFixture.createOrganizerBody(email), adminToken);
        return support.loginAndGetToken(email, AuthFixture.VALID_PASSWORD);
    }

    /** 새 이메일로 회원가입하고 로그인해 토큰을 받는다 (기본 USER Role) */
    public static String memberToken(ApiTestSupport support) {
        String email = AuthFixture.newEmail();
        support.post("/api/v1/auth/signup", AuthFixture.signUpBody(email));
        return support.loginAndGetToken(email, AuthFixture.VALID_PASSWORD);
    }
}
