package com.team1.recommendation.notification.service;

import com.team1.recommendation.common.ApiException;
import com.team1.recommendation.common.ErrorCode;
import com.team1.recommendation.expo.client.ExpoSummary;
import com.team1.recommendation.expo.client.InternalExpoClient;
import com.team1.recommendation.expo.entity.ExpoTag;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import com.team1.recommendation.notification.dto.NotificationItem;
import com.team1.recommendation.notification.dto.NotificationListResponse;
import com.team1.recommendation.notification.entity.Notification;
import com.team1.recommendation.notification.repository.NotificationRepository;
import com.team1.recommendation.score.repository.UserPreferenceScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final double MIN_SCORE_THRESHOLD = 1.0;

    private final NotificationRepository repository;
    private final UserPreferenceScoreRepository scoreRepository;
    private final ExpoTagRepository expoTagRepository;
    private final InternalExpoClient expoClient;

    public NotificationService(NotificationRepository repository,
                               UserPreferenceScoreRepository scoreRepository,
                               ExpoTagRepository expoTagRepository,
                               InternalExpoClient expoClient) {
        this.repository = repository;
        this.scoreRepository = scoreRepository;
        this.expoTagRepository = expoTagRepository;
        this.expoClient = expoClient;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Slice<Notification> slice = unreadOnly
                ? repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<NotificationItem> items = slice.getContent().stream()
                .map(NotificationItem::from)
                .toList();
        return new NotificationListResponse(items, slice.hasNext());
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "notification not found: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "not your notification");
        }
        notification.markRead();
    }

    // 매일 09:00 — 관심 점수 높은 사용자에게 신규 박람회 알림 생성
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void generateNotifications() {
        log.info("notification generation started");
        List<ExpoSummary> expos = expoClient.listPublished();
        if (expos.isEmpty()) return;

        // expoId → 태그 목록
        Map<Long, List<String>> tagMap = expoTagRepository.findAll().stream()
                .filter(t -> !"UNTAGGED".equals(t.getTagValue()))
                .collect(Collectors.groupingBy(
                        ExpoTag::getExpoId,
                        Collectors.mapping(ExpoTag::getTagValue, Collectors.toList())));

        LocalDateTime now = LocalDateTime.now();

        for (ExpoSummary expo : expos) {
            List<String> tags = tagMap.get(expo.expoId());
            if (tags == null || tags.isEmpty()) continue;

            // 태그 중 하나라도 점수가 있는 사용자에게 알림
            for (String tag : tags) {
                scoreRepository.findByTagValueAndScoreGreaterThan(tag, MIN_SCORE_THRESHOLD)
                        .forEach(score -> {
                            Long userId = score.getUserId();
                            if (repository.existsByUserIdAndExpoId(userId, expo.expoId())) return;
                            String message = "관심 분야 박람회가 열렸습니다: " + expo.title();
                            repository.save(Notification.of(userId, expo.expoId(), message, now));
                        });
            }
        }
        log.info("notification generation done");
    }
}
