package com.team1.identity.organizer.dto;

import com.team1.identity.organizer.entity.OrganizerApplication;
import com.team1.identity.user.entity.User;

import java.time.LocalDateTime;

public record OrganizerApplicationResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String status,
        String reason,
        String rejectReason,
        Long reviewerId,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {

    public static OrganizerApplicationResponse from(OrganizerApplication a) {
        return new OrganizerApplicationResponse(
                a.getId(), a.getUserId(), null, null, a.getStatus().name(), a.getReason(),
                a.getRejectReason(), a.getReviewerId(), a.getReviewedAt(), a.getCreatedAt());
    }

    public static OrganizerApplicationResponse from(OrganizerApplication a, User applicant) {
        return new OrganizerApplicationResponse(
                a.getId(), a.getUserId(), applicant.getName(), applicant.getEmail(), a.getStatus().name(),
                a.getReason(), a.getRejectReason(), a.getReviewerId(), a.getReviewedAt(), a.getCreatedAt());
    }
}
