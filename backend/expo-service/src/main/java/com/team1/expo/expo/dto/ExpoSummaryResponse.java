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
        LocalDateTime createdAt
) {
    public static ExpoSummaryResponse from(Expo expo) {
        return new ExpoSummaryResponse(
                expo.getId(),
                expo.getChannelId(),
                expo.getTitle(),
                expo.getVenue(),
                expo.getRegion(),
                expo.getCategory(),
                expo.getThumbnailUrl(),
                expo.getCreatedAt()
        );
    }
}
