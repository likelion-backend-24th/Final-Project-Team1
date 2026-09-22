package com.team1.expo.expo.service;

import com.team1.expo.client.RoundClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoCategories;
import com.team1.expo.domain.expo.ExpoStatus;
import com.team1.expo.expo.dto.DeadlineSortResult;
import com.team1.expo.expo.dto.ExpoDetailResponse;
import com.team1.expo.expo.dto.ExpoFeeView;
import com.team1.expo.expo.dto.ExpoSummaryResponse;
import com.team1.expo.expo.dto.RoundView;
import com.team1.expo.expo.repository.ExpoQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpoQueryService {

    private static final int MAX_SIZE = 100;

    private final ExpoQueryRepository expoQueryRepository;
    private final RoundClient roundClient;

    /**
     * 공개(PUBLISHED) 박람회 목록.
     * sort: recommended(기본·추천순), newest(새행사순), deadline(모집마감일순)
     * VIP 상단 노출은 GET /api/v1/expo-promotions/active 를 프론트가 별도 호출해 조합한다.
     * deadline 정렬은 회차의 가장 가까운 마감일을 reservation-service 에서 일괄로 받아 매긴다.
     */
    public Page<ExpoSummaryResponse> listPublished(String region, String category, String keyword, String sort, int page, int size) {
        if (category != null && !ExpoCategories.contains(category)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String q = normalizeKeyword(keyword);
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), MAX_SIZE);

        if ("deadline".equals(sort)) {
            List<Long> allIds = expoQueryRepository.findPublishedIds(region, category, q);
            if (allIds.isEmpty()) {
                return new PageImpl<>(List.of(), PageRequest.of(pageIndex, pageSize), 0);
            }
            DeadlineSortResult sorted = roundClient.deadlineSort(allIds, page, pageSize);
            if (sorted.expoIds().isEmpty()) {
                return new PageImpl<>(List.of(), PageRequest.of(pageIndex, pageSize), sorted.totalElements());
            }
            Map<Long, Expo> expoMap = expoQueryRepository.findByIdIn(sorted.expoIds()).stream()
                    .collect(Collectors.toMap(Expo::getId, e -> e));
            List<Expo> slice = sorted.expoIds().stream()
                    .map(expoMap::get)
                    .filter(e -> e != null)
                    .toList();
            Map<Long, Boolean> paidSlice = paidFlags(slice);
            return new PageImpl<>(
                    slice.stream().map(e -> ExpoSummaryResponse.from(e, paidSlice.get(e.getId()))).toList(),
                    PageRequest.of(pageIndex, pageSize), sorted.totalElements());
        }

        Sort ordering = switch (sort == null ? "recommended" : sort) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt"); // recommended
        };

        Pageable pageable = PageRequest.of(pageIndex, pageSize, ordering);
        Page<Expo> found = expoQueryRepository.findPublished(region, category, q, pageable);
        Map<Long, Boolean> paidByExpoId = paidFlags(found.getContent());
        return found.map(expo -> ExpoSummaryResponse.from(expo, paidByExpoId.get(expo.getId())));
    }

    /**
     * 검색어를 LIKE 에 넣기 전에 다듬는다.
     *
     * <p>공백만 있는 검색어는 조건 자체를 버린다 - 그대로 두면 {@code like '%   %'} 가 되어
     * 아무것도 안 나온다. 그리고 {@code %}·{@code _} 를 막지 않으면 사용자가 친 "50%" 가
     * 와일드카드로 동작한다. <b>자연어 검색이 붙으면 LLM 이 만든 문자열도 같은 LIKE 로 들어온다.</b>
     */
    public static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }


    /**
     * 목록 한 페이지의 유료/무료를 한 번에 받아온다. 박람회당 호출하면 페이지당 수십 번이 된다.
     * 실패하면 빈 Map - 배지만 사라지고 목록은 그대로 나간다(부분 실패 허용).
     */
    public Map<Long, Boolean> paidFlags(List<Expo> expos) {
        if (expos.isEmpty()) {
            return Map.of();
        }
        try {
            return roundClient.feeSummaries(expos.stream().map(Expo::getId).toList())
                    .stream()
                    .collect(Collectors.toMap(ExpoFeeView::expoId, ExpoFeeView::paid));
        } catch (BusinessException e) {
            return Map.of();
        }
    }

    /**
     * 공개 박람회 상세. PUBLISHED가 아니면 404(HIDDEN·CLOSED 구분 없이 존재를 드러내지 않음).
     * 회차 조회가 실패해도 기본 정보는 200으로 반환하고 roundsAvailable=false로 구분한다(부분 실패 허용).
     */
    public ExpoDetailResponse getPublishedExpo(Long expoId) {
        Expo expo = expoQueryRepository.findById(expoId)
                .filter(e -> e.getStatus() == ExpoStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        try {
            List<RoundView> rounds = roundClient.listByExpo(expoId);
            return ExpoDetailResponse.of(expo, true, rounds);
        } catch (BusinessException e) {
            // 회차 조회 실패는 전체 실패로 번지지 않는다.
            return ExpoDetailResponse.of(expo, false, null);
        }
    }
}
