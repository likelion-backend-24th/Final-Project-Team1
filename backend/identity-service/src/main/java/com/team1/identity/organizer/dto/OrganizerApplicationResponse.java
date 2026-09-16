package com.team1.identity.organizer.dto;

import com.team1.identity.organizer.entity.OrganizerApplication;

import java.time.LocalDateTime;

public record OrganizerApplicationResponse(
        Long id,
        Long userId,
        String status,
        String reason,
        String rejectReason,
        Long reviewerId,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {

    public static OrganizerApplicationResponse from(OrganizerApplication a) {
        return new OrganizerApplicationResponse(
                a.getId(), a.getUserId(), a.getStatus().name(), a.getReason(),
                a.getRejectReason(), a.getReviewerId(), a.getReviewedAt(), a.getCreatedAt());
    }
}
