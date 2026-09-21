package com.team1.expo.expo.search;

import java.time.LocalDate;

/**
 * 시스템이 문장을 어떻게 읽었는지 화면에 알려주는 값.
 *
 * <p><b>이 응답이 이 기능의 핵심이다.</b> 모델이 "부산" 을 "부천" 으로 읽으면 방문자는 결과가
 * 이상한 이유를 알 수 없다. 해석을 드러내야 틀린 조건을 보고 지울 수 있다.
 */
public record SearchInterpretation(String region,
                                   String category,
                                   Boolean paid,
                                   LocalDate dateFrom,
                                   LocalDate dateTo,
                                   String keyword) {

    public static SearchInterpretation from(SearchFilter filter) {
        return new SearchInterpretation(filter.region(), filter.category(), filter.paid(),
                filter.dateFrom(), filter.dateTo(), filter.keyword());
    }
}
