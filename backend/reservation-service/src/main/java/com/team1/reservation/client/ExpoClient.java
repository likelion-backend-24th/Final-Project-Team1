package com.team1.reservation.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;


public interface ExpoClient {


    ExpoSummary getExpo(Long expoId);

    /**
     * 박람회 제목 일괄 조회. 내 예약 화면 표시용이라 <b>실패해도 예외를 던지지 않고</b>
     * 빈 Map 을 돌려준다 - 제목 하나 때문에 예약 목록이 막히면 안 된다.
     */
    Map<Long, String> titles(Collection<Long> expoIds);

    /**
     * 박람회 카테고리 일괄 조회. 캘린더 추천이 카테고리 조건으로 후보를 거르는 데 쓴다.
     * 실패해도 예외를 던지지 않고 빈 Map 을 돌려준다 - 카테고리를 모르면 그 조건만 못 거를 뿐,
     * 추천 전체가 막히면 안 된다.
     */
    Map<Long, String> categories(Collection<Long> expoIds);

    /**
     * 박람회를 비공개로 되돌린다(계약 2-3). 마지막 회차를 삭제하기 <b>직전</b>에 부른다.
     * 멱등이며, 실패하면 예외를 던진다 - 호출부는 삭제를 중단해야 한다(fail-closed).
     */
    void unpublish(Long expoId);

    /**
    * 공개(PUBLISHED)박람회 id 전체 조회.
    * 캘린더 추천의 후보 범위를 정하는데 쓴다.
    * 실패해도 예외를 던지지않고 빈 리스트 되돌려줌 - 아예안뜨는거보다 후보없는 빈캘린더.
    */
    List<Long> publishedExpoIds();
}
