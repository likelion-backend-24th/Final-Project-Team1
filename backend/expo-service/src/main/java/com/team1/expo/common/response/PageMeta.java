package com.team1.expo.common.response;

import org.springframework.data.domain.Page;

/**
 * 목록 응답의 페이지 정보를 담아 ApiResponse.meta로 전달한다.
 * page는 계약에 따라 1부터 시작하는 값을 담는다.
 */
public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageMeta of(int page, Page<?> springPage) {
        return new PageMeta(page, springPage.getSize(), springPage.getTotalElements(), springPage.getTotalPages());
    }
}
