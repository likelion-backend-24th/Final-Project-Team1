package com.team1.expo.domain.expo;

import jakarta.persistence.*;
import lombok.AccessLevel;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // 행사 소개용 상세 이미지. 순서가 곧 화면에 쌓이는 순서다.
    // 독립 조회가 없는 종속 값이라 ElementCollection 으로 둔다 - 박람회와 함께 살고 죽는다.
    // 목록 응답에도 실리므로 박람회마다 쿼리가 하나씩 더 나가지 않게 묶어 읽는다.
    @BatchSize(size = 30)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "expo_images", joinColumns = @JoinColumn(name = "expo_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false, length = 500)
    private List<String> detailImageUrls = new ArrayList<>();

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
        e.detailImageUrls = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /**
     * 부분 수정. <b>null 은 "그대로 두기"</b> 이고 빈 문자열은 "지우기" 다.
     * status·channelId 는 여기서 바꾸지 않는다 - 공개 전환은 publish() 가 담당한다.
     */
    public void update(String title, String description, String venue,
                       String region, String category, String thumbnailUrl) {
        if (this.status == ExpoStatus.CLOSED) {
            throw new IllegalStateException("CLOSED expo cannot be updated");
        }
        if (title != null) {
            if (title.isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            this.title = title;
        }
        if (category != null) {
            if (category.isBlank()) {
                throw new IllegalArgumentException("category must not be blank");
            }
            this.category = category;
        }
        if (description != null) {
            this.description = description;
        }
        if (venue != null) {
            this.venue = venue;
        }
        if (region != null) {
            this.region = region;
        }
        if (thumbnailUrl != null) {
            this.thumbnailUrl = thumbnailUrl;
        }
        this.updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    /**
     * 상세 이미지를 통째로 교체한다. 순서 바꾸기·삭제·추가가 "새 목록을 보낸다" 하나로 끝난다.
     * 관리 중인 컬렉션을 갈아끼우면 Hibernate 가 추적을 놓치므로 비우고 채운다.
     */
    public void replaceDetailImages(List<String> urls) {
        this.detailImageUrls.clear();
        if (urls != null) {
            this.detailImageUrls.addAll(urls);
        }
        this.updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    public void publish() {
        if (this.status == ExpoStatus.CLOSED) {
            throw new IllegalStateException("CLOSED expo cannot be published");
        }
        this.status = ExpoStatus.PUBLISHED;
        this.updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    /**
     * 자동 비공개(S9-3). 마지막 회차가 삭제되면 예약-Service 가 이 경로를 트리거한다.
     * PUBLISHED 가 아니면 아무 일도 하지 않는다 - 재시도해도 상태·이력이 중복 변경되지 않아야 한다.
     */
    public void unpublish() {
        if (this.status != ExpoStatus.PUBLISHED) {
            return;
        }
        this.status = ExpoStatus.HIDDEN;
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
