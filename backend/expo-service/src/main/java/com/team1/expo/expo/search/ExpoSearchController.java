package com.team1.expo.expo.search;

import com.team1.expo.common.response.ApiResponse;
import com.team1.expo.common.response.PageMeta;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 자연어 검색 API. 인증이 필요 없고 PUBLISHED 박람회만 나온다.
 *
 * <p>경로가 {@code /api/v1/expos/search} 라 {@code /api/v1/expos/{expoId}} 와 겹쳐 보이지만,
 * Spring 은 고정 경로를 경로 변수보다 먼저 맞춘다.
 */
@RestController
@RequestMapping("/api/v1/expos")
public class ExpoSearchController {

    private final ExpoSearchService expoSearchService;

    public ExpoSearchController(ExpoSearchService expoSearchService) {
        this.expoSearchService = expoSearchService;
    }

    /**
     * {@code ignore} 는 방문자가 지운 해석 칩이다 - region · category · paid · date · keyword.
     * 모르는 값은 조용히 무시한다(해석 검증과 같은 태도).
     */
    @GetMapping("/search")
    public ApiResponse<ExpoSearchResponse> search(
            @RequestParam String q,
            @RequestParam(required = false) List<String> ignore,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        ExpoSearchService.Result result = expoSearchService.search(q, ignore, page, size);
        ExpoSearchResponse body = new ExpoSearchResponse(
                SearchInterpretation.from(result.filter()),
                result.aiApplied(),
                result.page().getContent());
        return ApiResponse.ok(body, PageMeta.of(page, result.page()));
    }
}
