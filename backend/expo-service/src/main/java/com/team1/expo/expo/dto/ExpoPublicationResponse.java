package com.team1.expo.expo.dto;

import com.team1.expo.domain.expo.Expo;

/**
 * 공개 전환(publishExpo) 결과. 멱등 재공개도 현재 상태를 그대로 담아 200으로 응답한다.
 */
public record ExpoPublicationResponse(
        Long expoId,
        String status
) {
    public static ExpoPublicationResponse from(Expo expo) {
        return new ExpoPublicationResponse(expo.getId(), expo.getStatus().name());
    }
}
