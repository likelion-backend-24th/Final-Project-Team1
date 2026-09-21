package com.team1.recommendation.common;

import java.util.List;
import java.util.Map;

public final class CategoryTagMap {

    public static final Map<String, List<String>> TAGS = Map.of(
            "IT·전자",  List.of("IT", "전자", "기술"),
            "식품·음료", List.of("식품", "음료"),
            "패션·뷰티", List.of("패션", "뷰티"),
            "교육·취업", List.of("교육", "취업", "채용"),
            "문화·예술", List.of("문화", "예술"),
            "기타",     List.of("기타")
    );

    private CategoryTagMap() {}
}
