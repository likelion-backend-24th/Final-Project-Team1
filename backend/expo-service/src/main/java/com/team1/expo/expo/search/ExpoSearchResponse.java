package com.team1.expo.expo.search;

import com.team1.expo.expo.dto.ExpoSummaryResponse;

import java.util.List;

/**
 * 자연어 검색 응답.
 *
 * <p>{@code aiApplied} 가 false 면 문장을 해석하지 못해 입력을 통째로 키워드로 검색했다는 뜻이다.
 * 화면은 그때 해석 칩을 띄우지 않는다 - 읽지도 못한 조건을 보여주면 거짓말이 된다.
 */
public record ExpoSearchResponse(SearchInterpretation interpreted,
                                 boolean aiApplied,
                                 List<ExpoSummaryResponse> expos) {
}
