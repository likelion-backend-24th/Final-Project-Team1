package com.team1.identity.organizer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "organizer_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizerApplicationStatus status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static OrganizerApplication create(Long userId, String reason, LocalDateTime now) {
        OrganizerApplication a = new OrganizerApplication();
        a.userId = userId;
        a.status = OrganizerApplicationStatus.PENDING;
        a.reason = reason;
        a.createdAt = now;
        return a;
    }

    public void approve(Long reviewerId, LocalDateTime now) {
        this.status = OrganizerApplicationStatus.APPROVED;
        this.reviewerId = reviewerId;
        this.reviewedAt = now;
    }

    public void reject(Long reviewerId, String rejectReason, LocalDateTime now) {
        this.status = OrganizerApplicationStatus.REJECTED;
        this.reviewerId = reviewerId;
        this.rejectReason = rejectReason;
        this.reviewedAt = now;
    }
}
