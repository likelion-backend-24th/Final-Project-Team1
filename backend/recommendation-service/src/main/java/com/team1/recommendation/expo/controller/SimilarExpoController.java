package com.team1.recommendation.expo.controller;

import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.expo.dto.SimilarExposResponse;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import com.team1.recommendation.expo.service.SimilarExpoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/expos")
public class SimilarExpoController {

    private final SimilarExpoService similarExpoService;
    private final ExpoTagRepository expoTagRepository;

    public SimilarExpoController(SimilarExpoService similarExpoService,
                                 ExpoTagRepository expoTagRepository) {
        this.similarExpoService = similarExpoService;
        this.expoTagRepository = expoTagRepository;
    }

    @GetMapping("/{expoId}/similar")
    public ApiResponse<SimilarExposResponse> getSimilar(@PathVariable Long expoId) {
        return ApiResponse.ok(similarExpoService.findSimilar(expoId));
    }

    @GetMapping("/{expoId}/tags")
    public ApiResponse<Map<String, List<String>>> getTags(@PathVariable Long expoId) {
        List<String> tags = expoTagRepository.findByExpoId(expoId).stream()
                .map(t -> t.getTagValue())
                .filter(v -> !"UNTAGGED".equals(v))
                .toList();
        return ApiResponse.ok(Map.of("tags", tags));
    }

    @GetMapping("/tags/bulk")
    public ApiResponse<Map<String, List<String>>> getBulkTags(@RequestParam List<Long> ids) {
        Map<String, List<String>> result = expoTagRepository.findByExpoIdIn(ids).stream()
                .filter(t -> !"UNTAGGED".equals(t.getTagValue()))
                .collect(Collectors.groupingBy(
                        t -> t.getExpoId().toString(),
                        Collectors.mapping(t -> t.getTagValue(), Collectors.toList())));
        return ApiResponse.ok(result);
    }
}
