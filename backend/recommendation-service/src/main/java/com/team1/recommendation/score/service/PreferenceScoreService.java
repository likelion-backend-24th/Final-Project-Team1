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
import org.springframework.scheduling.annotation.Async;
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
            EventType.RESERVATION_CONFIRMED, 4.0,
            EventType.PAGE_VIEWED, 0.5
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

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void recalculate() {
        log.info("preference score recalculation started");
        Map<Long, List<String>> tagMap = buildTagMap();
        activityRepository.findAll().stream()
                .collect(Collectors.groupingBy(UserActivity::getUserId))
                .forEach((userId, acts) -> saveScores(userId, computeScores(acts, tagMap)));
        log.info("preference score recalculation done");
    }

    @Async
    @Transactional
    public void recalculateForUser(Long userId) {
        Map<Long, List<String>> tagMap = buildTagMap();
        saveScores(userId, computeScores(activityRepository.findByUserId(userId), tagMap));
    }

    private Map<Long, List<String>> buildTagMap() {
        return expoTagRepository.findAll().stream()
                .filter(t -> !"UNTAGGED".equals(t.getTagValue()))
                .collect(Collectors.groupingBy(
                        ExpoTag::getExpoId,
                        Collectors.mapping(ExpoTag::getTagValue, Collectors.toList())));
    }

    private Map<String, Double> computeScores(List<UserActivity> activities, Map<Long, List<String>> tagMap) {
        Map<String, Double> tagScores = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserActivity act : activities) {
            List<String> tags = tagMap.get(act.getExpoId());
            if (tags == null || tags.isEmpty()) continue;
            double weight = WEIGHTS.getOrDefault(act.getEventType(), 1.0);
            double daysSince = Duration.between(act.getOccurredAt(), now).toDays();
            double decay = Math.pow(0.5, daysSince / HALF_LIFE_DAYS);
            double score = weight * decay;
            for (String tag : tags) {
                tagScores.merge(tag, score, Double::sum);
            }
        }
        return tagScores;
    }

    private void saveScores(Long userId, Map<String, Double> tagScores) {
        LocalDateTime now = LocalDateTime.now();
        tagScores.forEach((tagValue, score) ->
                scoreRepository.findByUserIdAndTagValue(userId, tagValue).ifPresentOrElse(
                        existing -> { existing.updateScore(score, now); scoreRepository.save(existing); },
                        () -> scoreRepository.save(UserPreferenceScore.of(userId, tagValue, score, now))
                )
        );
    }
}
