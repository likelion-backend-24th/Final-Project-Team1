package com.team1.recommendation.preference.repository;

import com.team1.recommendation.preference.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    void deleteAllByUserId(Long userId);
}
