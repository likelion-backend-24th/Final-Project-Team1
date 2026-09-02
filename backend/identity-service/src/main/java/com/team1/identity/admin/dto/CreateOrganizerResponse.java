package com.team1.identity.admin.dto;

public record CreateOrganizerResponse(
        Long userId,
        String email,
        String name,
        String role
) {
}
