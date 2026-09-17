package com.team1.recommendation.activity.repository;

import com.team1.recommendation.activity.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {}
