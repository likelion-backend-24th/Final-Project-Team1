package com.team1.expo.expo.draft;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 소개글 초안 요청.
 *
 * <p>키워드 말고 제목·분야·장소·지역도 받는다. 주최자가 이미 채운 값을 같이 넘겨야
 * "어디서 열리는 무슨 행사" 인지가 초안에 들어간다 - 키워드만으로는 뻔한 문장이 나온다.
 * 키워드를 뺀 나머지는 모두 선택값이다(등록 화면에서 아직 비어 있을 수 있다).
 */
public record DescriptionDraftRequest(
        @NotEmpty(message = "키워드를 하나 이상 넣어주세요")
        @Size(max = 10, message = "키워드는 10개까지입니다")
        List<@Size(max = 50) String> keywords,

        @Size(max = 200) String title,
        @Size(max = 50) String category,
        @Size(max = 200) String venue,
        @Size(max = 50) String region
) {}
