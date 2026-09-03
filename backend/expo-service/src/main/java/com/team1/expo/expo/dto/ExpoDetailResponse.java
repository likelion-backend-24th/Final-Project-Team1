package com.team1.expo.expo.dto;

import com.team1.expo.domain.expo.Expo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공개 박람회 상세(getExpo). 회차 목록·잔여 정원은 reservation-service에서 병합한다.
 * 회차 조회가 실패하면 박람회 기본 정보는 그대로 내려주고 roundsAvailable=false로 구분한다(부분 실패 허용).
 */
public record ExpoDetailResponse(
        Long expoId,
        Long channelId,
        String title,
        String description,
        String venue,
        String region,
        String category,
        String status,
        String thumbnailUrl,
        LocalDateTime createdAt,
        boolean roundsAvailable,
        List<RoundView> rounds
) {
    public static ExpoDetailResponse of(Expo expo, boolean roundsAvailable, List<RoundView> rounds) {
        return new ExpoDetailResponse(
                expo.getId(),
                expo.getChannelId(),
                expo.getTitle(),
                expo.getDescription(),
                expo.getVenue(),
                expo.getRegion(),
                expo.getCategory(),
                expo.getStatus().name(),
                expo.getThumbnailUrl(),
                expo.getCreatedAt(),
                roundsAvailable,
                rounds
        );
    }
}
