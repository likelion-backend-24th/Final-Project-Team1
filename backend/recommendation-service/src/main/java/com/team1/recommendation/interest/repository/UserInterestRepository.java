package com.team1.recommendation.interest.repository;

import com.team1.recommendation.interest.entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {
    void deleteAllByUserId(Long userId);
}
