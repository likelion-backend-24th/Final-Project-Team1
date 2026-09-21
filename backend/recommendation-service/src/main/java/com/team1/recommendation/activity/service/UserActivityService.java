package com.team1.recommendation.activity.service;

import com.team1.recommendation.activity.entity.UserActivity;
import com.team1.recommendation.activity.repository.UserActivityRepository;
import com.team1.recommendation.internal.dto.BehaviorEventRequest;
import com.team1.recommendation.score.service.PreferenceScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserActivityService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityService.class);

    private final UserActivityRepository repository;
    private final PreferenceScoreService preferenceScoreService;

    public UserActivityService(UserActivityRepository repository,
                               PreferenceScoreService preferenceScoreService) {
        this.repository = repository;
        this.preferenceScoreService = preferenceScoreService;
    }

    public void record(BehaviorEventRequest request) {
        log.info("behavior event received userId={} expoId={} type={}",
                request.userId(), request.expoId(), request.eventType());
        try {
            repository.saveAndFlush(UserActivity.of(
                    request.userId(), request.expoId(), request.eventType(), LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            log.info("duplicate activity ignored userId={} expoId={} type={}",
                    request.userId(), request.expoId(), request.eventType());
            return;
        }
        preferenceScoreService.recalculateForUser(request.userId());
    }
}
