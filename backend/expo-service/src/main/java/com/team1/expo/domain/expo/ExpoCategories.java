package com.team1.expo.domain.expo;

import java.util.Set;

/**
 * 박람회 분야 목록 한 곳.
 *
 * <p>같은 목록이 CreateExpoRequest·UpdateExpoRequest 의 {@code @Pattern} 정규식에도 있다.
 * 애너테이션 값은 상수여야 해서 Set 을 참조할 수 없으므로 그쪽은 문자열로 남는다.
 * <b>분야가 바뀌면 이 상수와 두 정규식을 함께 고쳐야 한다.</b>
 */
public final class ExpoCategories {

    public static final Set<String> ALLOWED =
            Set.of("IT·전자", "식품·음료", "패션·뷰티", "교육·취업", "문화·예술", "기타");

    private ExpoCategories() {
    }

    public static boolean contains(String category) {
        return category != null && ALLOWED.contains(category);
    }
}
