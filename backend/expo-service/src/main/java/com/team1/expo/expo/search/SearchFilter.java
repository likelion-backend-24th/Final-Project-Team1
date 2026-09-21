package com.team1.expo.expo.search;

import java.time.LocalDate;
import java.util.Collection;

/**
 * 검색 문장을 옮겨 담은 필터. 값이 null 이면 그 조건을 쓰지 않는다는 뜻이다.
 *
 * <p>여기 담긴 값은 전부 <b>시스템이 아는 값</b>이다 - 지역·카테고리는 실제 목록에서 고른 것이고,
 * 날짜는 절대 날짜로 바뀐 뒤다. 이 뒤로는 평범한 목록 조회라 LLM 이 개입할 자리가 없다.
 */
public record SearchFilter(String region,
                           String category,
                           Boolean paid,
                           LocalDate dateFrom,
                           LocalDate dateTo,
                           String keyword) {

    /** 해석에 실패했을 때. 문장을 통째로 키워드로 넘겨 검색은 되게 한다. */
    public static SearchFilter keywordOnly(String keyword) {
        return new SearchFilter(null, null, null, null, null, keyword);
    }

    /** 하나도 못 뽑았다면 LLM 이 개입하지 않은 것과 같다. */
    public boolean isEmpty() {
        return region == null && category == null && paid == null
                && dateFrom == null && dateTo == null;
    }

    public boolean hasDateRange() {
        return dateFrom != null && dateTo != null;
    }

    /**
     * 방문자가 해석 칩을 지웠을 때 그 조건만 뺀다.
     *
     * <p>지우기를 화면에서 처리할 수 없어 여기에 둔다 - 조건을 빼면 결과는 <b>넓어져야</b> 하는데,
     * 이미 좁혀서 받은 목록을 화면에서 더 거르는 걸로는 넓힐 수 없다. 문장은 그대로 다시 오므로
     * 해석 캐시에 걸려 LLM 을 다시 부르지도 않는다.
     */
    public SearchFilter without(Collection<String> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return this;
        }
        boolean date = conditions.contains("date");
        return new SearchFilter(
                conditions.contains("region") ? null : region,
                conditions.contains("category") ? null : category,
                conditions.contains("paid") ? null : paid,
                date ? null : dateFrom,
                date ? null : dateTo,
                conditions.contains("keyword") ? null : keyword);
    }
}
