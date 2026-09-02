package com.team1.expo.domain.expo;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "expos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long channelId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String venue;

    @Column(length = 50)
    private String region;

    @Column(nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpoStatus status;

    @Column(length = 500)
    private String thumbnailUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;

    public static Expo create(Long channelId, String title, String description,
                              String venue, String region, String category, String thumbnailUrl) {
        Expo e = new Expo();
        e.channelId = channelId;
        e.title = title;
        e.description = description;
        e.venue = venue;
        e.region = region;
        e.category = category;
        e.thumbnailUrl = thumbnailUrl;
        e.status = ExpoStatus.HIDDEN;
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void publish() {
        if (this.status == ExpoStatus.CLOSED) {
            throw new IllegalStateException("CLOSED expo cannot be published");
        }
        this.status = ExpoStatus.PUBLISHED;
        this.updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    public void close() {
        if (this.status != ExpoStatus.PUBLISHED) {
            return;
        }
        this.status = ExpoStatus.CLOSED;
        this.closedAt = LocalDateTime.now(Clock.systemUTC());
        this.updatedAt = this.closedAt;
    }
}
