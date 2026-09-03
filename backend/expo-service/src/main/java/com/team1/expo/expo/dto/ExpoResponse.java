package com.team1.expo.expo.dto;

import com.team1.expo.domain.expo.Expo;

import java.time.LocalDateTime;

public record ExpoResponse(
        Long id,
        Long channelId,
        String title,
        String description,
        String venue,
        String region,
        String category,
        String status,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ExpoResponse from(Expo expo) {
        return new ExpoResponse(
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
                expo.getUpdatedAt()
        );
    }
}
