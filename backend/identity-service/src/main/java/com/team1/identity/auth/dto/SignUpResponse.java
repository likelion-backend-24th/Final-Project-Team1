package com.team1.identity.auth.dto;

public record SignUpResponse(
        Long userId,
        String email,
        String name,
        String role
) {
}
