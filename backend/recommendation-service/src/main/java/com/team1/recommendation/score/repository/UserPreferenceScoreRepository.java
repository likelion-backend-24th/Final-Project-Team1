package com.team1.recommendation.score.repository;

import com.team1.recommendation.score.entity.UserPreferenceScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPreferenceScoreRepository extends JpaRepository<UserPreferenceScore, Long> {

    List<UserPreferenceScore> findByUserId(Long userId);

    Optional<UserPreferenceScore> findByUserIdAndTagValue(Long userId, String tagValue);

    List<UserPreferenceScore> findByTagValueAndScoreGreaterThan(String tagValue, double minScore);
}
