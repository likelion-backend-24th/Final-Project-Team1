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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final GeminiMessageService geminiMessageService;
    private final Clock clock;
    private final TransactionTemplate txTemplate;

    public NotificationService(NotificationRepository repository,
                               UserPreferenceScoreRepository scoreRepository,
                               ExpoTagRepository expoTagRepository,
                               InternalExpoClient expoClient,
                               GeminiMessageService geminiMessageService,
                               Clock clock,
                               PlatformTransactionManager txManager) {
        this.repository = repository;
        this.scoreRepository = scoreRepository;
        this.expoTagRepository = expoTagRepository;
        this.expoClient = expoClient;
        this.geminiMessageService = geminiMessageService;
        this.clock = clock;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Slice<Notification> slice = unreadOnly
                ? repository.findByUserIdAndIsReadFalseOrderByCreatedAtDescIdDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

        List<NotificationItem> items = slice.getContent().stream()
                .map(NotificationItem::from)
                .toList();
        return new NotificationListResponse(items, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
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

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId);
    }

    public void notifyReservationConfirmed(Long userId, Long expoId, Long reservationId, String reservationNo) {
        if (repository.existsByUserIdAndDedupKey(userId, Notification.reservationConfirmedKey(reservationId))) {
            return;
        }
        try {
            repository.saveAndFlush(Notification.reservationConfirmed(
                    userId, expoId, reservationId, reservationConfirmedMessage(reservationNo), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            log.info("reservation notification already exists userId={} reservationId={}", userId, reservationId);
        }
    }

    private static String reservationConfirmedMessage(String reservationNo) {
        String no = (reservationNo == null || reservationNo.isBlank()) ? "" : " (예약번호 " + reservationNo + ")";
        return "예약이 확정되었어요" + no + ". 내 예약에서 QR 티켓을 확인하세요.";
    }

    /**
     * 매일 09:00 — 관심 점수 높은 사용자에게 신규 박람회 알림 생성.
     * HTTP 호출은 트랜잭션 밖에서 수행, 박람회별로 독립 트랜잭션 커밋.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void generateNotifications() {
        log.info("notification generation started");

        // 1) HTTP 호출 — 트랜잭션 없는 상태에서 수행
        List<ExpoSummary> expos = expoClient.listPublished();
        if (expos.isEmpty()) return;

        // 2) 태그 맵 로드 — 짧은 read-only 트랜잭션
        Map<Long, List<String>> tagMap = txTemplate.execute(status -> {
            status.setRollbackOnly();
            return expoTagRepository.findAll().stream()
                    .filter(t -> !"UNTAGGED".equals(t.getTagValue()))
                    .collect(Collectors.groupingBy(
                            ExpoTag::getExpoId,
                            Collectors.mapping(ExpoTag::getTagValue, Collectors.toList())));
        });
        if (tagMap == null) return;

        Instant now = clock.instant();
        int saved = 0;

        // 3) 박람회별 독립 트랜잭션 — 한 expo 실패해도 나머지에 영향 없음
        for (ExpoSummary expo : expos) {
            List<String> tags = tagMap.get(expo.expoId());
            if (tags == null || tags.isEmpty()) continue;
            saved += saveForExpo(expo, tags, now);
        }
        log.info("notification generation done saved={}", saved);
    }

    private int saveForExpo(ExpoSummary expo, List<String> tags, Instant now) {
        // 알림 받을 사용자 먼저 조회 — 없으면 LLM 호출 생략
        Map<Long, List<String>> userMatchedTags = new HashMap<>();
        for (String tag : tags) {
            scoreRepository.findByTagValueAndScoreGreaterThan(tag, MIN_SCORE_THRESHOLD)
                    .forEach(score -> userMatchedTags
                            .computeIfAbsent(score.getUserId(), k -> new ArrayList<>())
                            .add(tag));
        }
        if (userMatchedTags.isEmpty()) return 0;

        // LLM 호출은 트랜잭션 밖, 알림 받을 사용자가 있을 때만 수행
        String llmMessage = geminiMessageService.generateNotificationMessage(expo.title(), tags);

        Integer count = txTemplate.execute(status -> {
            int n = 0;
            for (Map.Entry<Long, List<String>> entry : userMatchedTags.entrySet()) {
                Long userId = entry.getKey();
                List<String> matchedTags = entry.getValue();
                if (repository.existsByUserIdAndDedupKey(userId, Notification.recommendationKey(expo.expoId()))) continue;
                List<String> distinctTags = matchedTags.stream().distinct().limit(3).toList();
                String message = llmMessage != null ? llmMessage : buildFallbackMessage(expo.title(), distinctTags);
                repository.save(Notification.recommendation(userId, expo.expoId(), message, now));
                n++;
            }
            return n;
        });
        return count != null ? count : 0;
    }

    private String buildFallbackMessage(String expoTitle, List<String> matchedTags) {
        String tagStr = matchedTags.stream().map(t -> "#" + t).collect(Collectors.joining(" "));
        return "'" + expoTitle + "' 박람회가 열렸어요! " + tagStr + " 관심사와 딱 맞아요.";
    }
}
