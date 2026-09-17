package com.team1.expo.expo.dto;

import com.team1.expo.domain.expo.Expo;

import java.time.LocalDateTime;

/**
 * 공개 박람회 목록(listExpos)의 한 항목.
 */
public record ExpoSummaryResponse(
        Long expoId,
        Long channelId,
        String title,
        String venue,
        String region,
        String category,
        String thumbnailUrl,
        LocalDateTime createdAt,
        Boolean paid
) {
    /** 회차를 조회하지 못했거나 예약 가능한 회차가 없으면 paid=null - 프론트가 배지를 숨긴다. */
    public static ExpoSummaryResponse from(Expo expo, Boolean paid) {
        return new ExpoSummaryResponse(
                expo.getId(),
                expo.getChannelId(),
                expo.getTitle(),
                expo.getVenue(),
                expo.getRegion(),
                expo.getCategory(),
                expo.getThumbnailUrl(),
                expo.getCreatedAt(),
                paid
        );
    }
}
