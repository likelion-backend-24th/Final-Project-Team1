package com.team1.identity.common.util;

import java.util.Locale;

/**
 * 이메일을 저장·조회 전에 한 가지 형태로 통일한다.
 *
 * users.email의 UNIQUE 제약은 대소문자를 구분하므로, 정규화하지 않으면
 * Test1@team1.local 과 test1@team1.local 이 서로 다른 계정으로 가입된다.
 * 가입·발급·로그인이 모두 같은 규칙을 써야 하므로 한 곳에 둔다.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
