package com.team1.recommendation.recommendation.service;

import com.team1.recommendation.activity.entity.EventType;
import com.team1.recommendation.activity.repository.UserActivityRepository;
import com.team1.recommendation.expo.client.ExpoSummary;
import com.team1.recommendation.expo.client.InternalExpoClient;
import com.team1.recommendation.expo.entity.ExpoTag;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import com.team1.recommendation.preference.entity.UserPreference;
import com.team1.recommendation.preference.repository.UserPreferenceRepository;
import com.team1.recommendation.recommendation.dto.RecommendationItem;
import com.team1.recommendation.recommendation.dto.RecommendationResponse;
import com.team1.recommendation.score.repository.UserPreferenceScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final UserPreferenceScoreRepository scoreRepository;
    private final ExpoTagRepository expoTagRepository;
    private final InternalExpoClient expoClient;
    private final UserPreferenceRepository preferenceRepository;
    private final UserActivityRepository activityRepository;

    public RecommendationService(UserPreferenceScoreRepository scoreRepository,
                                 ExpoTagRepository expoTagRepository,
                                 InternalExpoClient expoClient,
                                 UserPreferenceRepository preferenceRepository,
                                 UserActivityRepository activityRepository) {
        this.scoreRepository = scoreRepository;
        this.expoTagRepository = expoTagRepository;
        this.expoClient = expoClient;
        this.preferenceRepository = preferenceRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long userId, int size) {
        Map<String, Double> userScores = scoreRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(s -> s.getTagValue(), s -> s.getScore()));

        // cold start: 행동 이력 없으면 수동 관심사로 초기 점수 구성
        if (userScores.isEmpty()) {
            List<UserPreference> interests = preferenceRepository.findByUserId(userId);
            if (interests.isEmpty()) return new RecommendationResponse(List.of(), null);
            for (UserPreference p : interests) {
                userScores.put(p.getValue().toLowerCase(), 1.0);
            }
        }

        // 이미 예약한 박람회는 제외
        Set<Long> reservedExpoIds = activityRepository.findByUserId(userId).stream()
                .filter(a -> a.getEventType() == EventType.RESERVATION_CONFIRMED)
                .map(a -> a.getExpoId())
                .collect(Collectors.toSet());

        Map<Long, List<String>> tagMap = expoTagRepository.findAll().stream()
                .filter(t -> !"UNTAGGED".equals(t.getTagValue()))
                .collect(Collectors.groupingBy(
                        ExpoTag::getExpoId,
                        Collectors.mapping(ExpoTag::getTagValue, Collectors.toList())));

        List<ExpoSummary> expos = expoClient.listPublished();

        final Map<String, Double> scores = userScores;
        List<RecommendationItem> items = expos.stream()
                .filter(expo -> !reservedExpoIds.contains(expo.expoId()))
                .map(expo -> {
                    List<String> tags = tagMap.get(expo.expoId());
                    if (tags == null || tags.isEmpty()) return null;

                    List<String> matched = tags.stream()
                            .filter(t -> scores.containsKey(t) || scores.containsKey(t.toLowerCase()))
                            .toList();
                    double score = matched.stream()
                            .mapToDouble(t -> scores.getOrDefault(t, scores.getOrDefault(t.toLowerCase(), 0.0)))
                            .sum();
                    if (score <= 0) return null;

                    return new RecommendationItem(expo.expoId(), expo.title(), matched, score);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RecommendationItem::score).reversed())
                .limit(size)
                .toList();

        return new RecommendationResponse(items, LocalDateTime.now());
    }
}
