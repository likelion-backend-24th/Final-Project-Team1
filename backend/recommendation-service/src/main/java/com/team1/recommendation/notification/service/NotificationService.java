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
import org.springframework.transaction.annotation.Transactional;

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

    public NotificationService(NotificationRepository repository,
                               UserPreferenceScoreRepository scoreRepository,
                               ExpoTagRepository expoTagRepository,
                               InternalExpoClient expoClient,
                               GeminiMessageService geminiMessageService,
                               Clock clock) {
        this.repository = repository;
        this.scoreRepository = scoreRepository;
        this.expoTagRepository = expoTagRepository;
        this.expoClient = expoClient;
        this.geminiMessageService = geminiMessageService;
        this.clock = clock;
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

    /**
     * 예약 확정 알림을 만든다. 같은 예약으로 다시 불려도 알림은 1건이다.
     *
     * <p>일부러 트랜잭션을 걸지 않는다. 동시에 두 번 들어와 유일 제약에 걸리면 저장만 실패시키고
     * 삼켜야 하는데, 바깥 트랜잭션이 있으면 그 실패가 전체를 rollback-only 로 만든다.
     */
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

    private String buildMessage(String expoTitle, List<String> matchedTags) {
        String llmMessage = geminiMessageService.generateNotificationMessage(expoTitle, matchedTags);
        if (llmMessage != null) return llmMessage;
        // Gemini 실패 시 템플릿 fallback
        String tagStr = matchedTags.stream().map(t -> "#" + t).collect(Collectors.joining(" "));
        return "'" + expoTitle + "' 박람회가 열렸어요! " + tagStr + " 관심사와 딱 맞아요.";
    }

    private static String reservationConfirmedMessage(String reservationNo) {
        String no = (reservationNo == null || reservationNo.isBlank()) ? "" : " (예약번호 " + reservationNo + ")";
        return "예약이 확정되었어요" + no + ". 내 예약에서 QR 티켓을 확인하세요.";
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

        Instant now = clock.instant();

        for (ExpoSummary expo : expos) {
            List<String> tags = tagMap.get(expo.expoId());
            if (tags == null || tags.isEmpty()) continue;

            // 사용자별 매칭 태그를 모아서 한 번에 알림 생성 — 태그가 여러 개 매칭되면 모두 문구에 포함
            Map<Long, List<String>> userMatchedTags = new HashMap<>();
            for (String tag : tags) {
                scoreRepository.findByTagValueAndScoreGreaterThan(tag, MIN_SCORE_THRESHOLD)
                        .forEach(score -> userMatchedTags
                                .computeIfAbsent(score.getUserId(), k -> new ArrayList<>())
                                .add(tag));
            }

            userMatchedTags.forEach((userId, matchedTags) -> {
                if (repository.existsByUserIdAndExpoId(userId, expo.expoId())) return;
                List<String> distinctTags = matchedTags.stream().distinct().limit(3).toList();
                String message = buildMessage(expo.title(), distinctTags);
                repository.save(Notification.recommendation(userId, expo.expoId(), message, now));
            });
        }
        log.info("notification generation done");
    }
}
