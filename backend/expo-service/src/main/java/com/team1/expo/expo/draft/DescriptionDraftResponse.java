package com.team1.expo.expo.draft;

/**
 * 소개글 초안 응답.
 *
 * <p>{@code applied} 가 false 면 초안을 만들지 못했다는 뜻이고 {@code description} 은 null 이다.
 * 이때도 200 이다 - 초안은 편의 기능이라 실패했다고 등록 자체를 막으면 안 된다.
 * 화면은 안내만 띄우고 입력창을 그대로 둔다.
 */
public record DescriptionDraftResponse(String description, boolean applied) {

    static DescriptionDraftResponse notApplied() {
        return new DescriptionDraftResponse(null, false);
    }

    static DescriptionDraftResponse of(String description) {
        return new DescriptionDraftResponse(description, true);
    }
}
