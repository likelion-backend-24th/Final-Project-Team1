package com.team1.recommendation.score.repository;

import com.team1.recommendation.score.entity.ScoreSource;
import com.team1.recommendation.score.entity.UserPreferenceScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPreferenceScoreRepository extends JpaRepository<UserPreferenceScore, Long> {

    List<UserPreferenceScore> findByUserId(Long userId);

    Optional<UserPreferenceScore> findByUserIdAndTagValueAndSource(Long userId, String tagValue, ScoreSource source);

    void deleteByUserIdAndSource(Long userId, ScoreSource source);

    List<UserPreferenceScore> findByTagValueAndScoreGreaterThan(String tagValue, double minScore);
}
