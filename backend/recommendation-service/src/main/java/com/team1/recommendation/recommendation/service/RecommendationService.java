package com.team1.recommendation.recommendation.service;

import com.team1.recommendation.expo.client.ExpoSummary;
import com.team1.recommendation.expo.client.InternalExpoClient;
import com.team1.recommendation.expo.entity.ExpoTag;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
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
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final UserPreferenceScoreRepository scoreRepository;
    private final ExpoTagRepository expoTagRepository;
    private final InternalExpoClient expoClient;

    public RecommendationService(UserPreferenceScoreRepository scoreRepository,
                                 ExpoTagRepository expoTagRepository,
                                 InternalExpoClient expoClient) {
        this.scoreRepository = scoreRepository;
        this.expoTagRepository = expoTagRepository;
        this.expoClient = expoClient;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long userId, int size) {
        // 취향 점수 (tagValue → score)
        Map<String, Double> userScores = scoreRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(s -> s.getTagValue(), s -> s.getScore()));

        if (userScores.isEmpty()) {
            return new RecommendationResponse(List.of(), null);
        }

        // expo_tags (expoId → 태그 목록)
        Map<Long, List<String>> tagMap = expoTagRepository.findAll().stream()
                .filter(t -> !"UNTAGGED".equals(t.getTagValue()))
                .collect(Collectors.groupingBy(
                        ExpoTag::getExpoId,
                        Collectors.mapping(ExpoTag::getTagValue, Collectors.toList())));

        // 활성 박람회 목록
        List<ExpoSummary> expos = expoClient.listPublished();

        List<RecommendationItem> items = expos.stream()
                .map(expo -> {
                    List<String> tags = tagMap.get(expo.expoId());
                    if (tags == null || tags.isEmpty()) return null;

                    List<String> matched = tags.stream().filter(userScores::containsKey).toList();
                    double score = matched.stream().mapToDouble(userScores::get).sum();
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
