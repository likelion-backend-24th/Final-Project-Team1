package com.team1.identity.user.dto;

public record MyProfileResponse(
        Long id,
        String email,
        String name,
        String role,
        String profileImageUrl
) {
}
