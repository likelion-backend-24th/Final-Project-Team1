package com.team1.recommendation.activity.service;

import com.team1.recommendation.activity.entity.UserActivity;
import com.team1.recommendation.activity.repository.UserActivityRepository;
import com.team1.recommendation.internal.dto.BehaviorEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserActivityService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityService.class);

    private final UserActivityRepository repository;

    public UserActivityService(UserActivityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(BehaviorEventRequest request) {
        log.info("behavior event received userId={} expoId={} type={}",
                request.userId(), request.expoId(), request.eventType());
        repository.save(UserActivity.of(
                request.userId(), request.expoId(), request.eventType(), LocalDateTime.now()));
    }
}
