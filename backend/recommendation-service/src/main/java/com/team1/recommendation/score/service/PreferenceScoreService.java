package com.team1.recommendation.score.service;

import com.team1.recommendation.activity.entity.EventType;
import com.team1.recommendation.activity.entity.UserActivity;
import com.team1.recommendation.activity.repository.UserActivityRepository;
import com.team1.recommendation.expo.entity.ExpoTag;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import com.team1.recommendation.score.entity.UserPreferenceScore;
import com.team1.recommendation.score.repository.UserPreferenceScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PreferenceScoreService {

    private static final Logger log = LoggerFactory.getLogger(PreferenceScoreService.class);

    private static final Map<EventType, Double> WEIGHTS = Map.of(
            EventType.CHECKED_IN, 5.0,
            EventType.RESERVATION_CONFIRMED, 4.0
    );
    private static final double HALF_LIFE_DAYS = 30.0;

    private final UserActivityRepository activityRepository;
    private final ExpoTagRepository expoTagRepository;
    private final UserPreferenceScoreRepository scoreRepository;

    public PreferenceScoreService(UserActivityRepository activityRepository,
                                  ExpoTagRepository expoTagRepository,
                                  UserPreferenceScoreRepository scoreRepository) {
        this.activityRepository = activityRepository;
        this.expoTagRepository = expoTagRepository;
        this.scoreRepository = scoreRepository;
    }

    // 매일 새벽 2시 실행
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void recalculate() {
        log.info("preference score recalculation started");
        List<UserActivity> activities = activityRepository.findAll();

        // expoId → 태그 목록 (UNTAGGED 제외)
        Map<Long, List<String>> tagMap = expoTagRepository.findAll().stream()
                .filter(t -> !"UNTAGGED".equals(t.getTagValue()))
                .collect(Collectors.groupingBy(
                        ExpoTag::getExpoId,
                        Collectors.mapping(ExpoTag::getTagValue, Collectors.toList())));

        // userId → tagValue → 점수 합산
        Map<Long, Map<String, Double>> userScores = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (UserActivity act : activities) {
            List<String> tags = tagMap.get(act.getExpoId());
            if (tags == null || tags.isEmpty()) continue;

            double weight = WEIGHTS.getOrDefault(act.getEventType(), 1.0);
            double daysSince = Duration.between(act.getOccurredAt(), now).toDays();
            double decay = Math.pow(0.5, daysSince / HALF_LIFE_DAYS);
            double score = weight * decay;

            Map<String, Double> scores = userScores.computeIfAbsent(act.getUserId(), k -> new HashMap<>());
            for (String tag : tags) {
                scores.merge(tag, score, Double::sum);
            }
        }

        // upsert
        userScores.forEach((userId, tagScores) ->
                tagScores.forEach((tagValue, score) ->
                        scoreRepository.findByUserIdAndTagValue(userId, tagValue).ifPresentOrElse(
                                existing -> { existing.updateScore(score, now); scoreRepository.save(existing); },
                                () -> scoreRepository.save(UserPreferenceScore.of(userId, tagValue, score, now))
                        )
                )
        );
        log.info("preference score recalculation done");
    }
}
