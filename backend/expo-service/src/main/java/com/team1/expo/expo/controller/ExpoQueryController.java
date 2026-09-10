package com.team1.expo.expo.controller;

import com.team1.expo.common.response.ApiResponse;
import com.team1.expo.common.response.PageMeta;
import com.team1.expo.expo.dto.ExpoDetailResponse;
import com.team1.expo.expo.dto.ExpoSummaryResponse;
import com.team1.expo.expo.service.ExpoQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공개 박람회 조회 API (P4, #26). 인증 없이 접근 가능하며 PUBLISHED 박람회만 노출한다.
 * 박람회 등록(createExpo, P2/#21)과 클래스를 분리해 병렬 작업 시 충돌을 피한다.
 */
@RestController
@RequestMapping("/api/v1/expos")
@RequiredArgsConstructor
public class ExpoQueryController {

    private final ExpoQueryService expoQueryService;

    /**
     * sort 값: recommended(추천순·기본), newest(새행사순), deadline(모집마감일순)
     * recommended 탭에서 VIP 상단 노출은 GET /api/v1/expo-promotions/active 를 별도 호출해 조합한다.
     */
    @GetMapping
    public ApiResponse<List<ExpoSummaryResponse>> listExpos(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "recommended") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ExpoSummaryResponse> result = expoQueryService.listPublished(region, category, sort, page, size);
        return ApiResponse.ok(result.getContent(), PageMeta.of(page, result));
    }

    @GetMapping("/{expoId}")
    public ApiResponse<ExpoDetailResponse> getExpo(@PathVariable Long expoId) {
        return ApiResponse.ok(expoQueryService.getPublishedExpo(expoId));
    }
}
