package com.team1.expo.expo.service;

import com.team1.expo.client.RoundClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoStatus;
import com.team1.expo.expo.dto.ExpoDetailResponse;
import com.team1.expo.expo.dto.ExpoSummaryResponse;
import com.team1.expo.expo.dto.RoundView;
import com.team1.expo.expo.repository.ExpoQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpoQueryService {

    private static final int MAX_SIZE = 100;

    // #21에서 확정된 카테고리 목록. createExpo(#21) 병합 시 공용 상수/Enum으로 통합 예정.
    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("IT·전자", "식품·음료", "패션·뷰티", "교육·취업", "문화·예술", "기타");

    private final ExpoQueryRepository expoQueryRepository;
    private final RoundClient roundClient;

    /**
     * 공개(PUBLISHED) 박람회 목록. page는 1부터, size는 기본 20·최대 100, created_at 내림차순 기본 정렬.
     */
    public Page<ExpoSummaryResponse> listPublished(String region, String category, int page, int size) {
        if (category != null && !ALLOWED_CATEGORIES.contains(category)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), MAX_SIZE);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        return expoQueryRepository.findPublished(region, category, pageable)
                .map(ExpoSummaryResponse::from);
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
