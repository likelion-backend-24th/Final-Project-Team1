package com.team1.recommendation.expo.service;

import com.team1.recommendation.expo.entity.ExpoTag;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import com.team1.recommendation.internal.dto.ExpoPublishedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ExpoTagService {

    private static final Logger log = LoggerFactory.getLogger(ExpoTagService.class);

    private final ExpoTagRepository repository;

    public ExpoTagService(ExpoTagRepository repository) {
        this.repository = repository;
    }

    // Sprint 4에서 LLM 태깅으로 교체 예정. 현재는 placeholder 태그 저장.
    @Transactional
    public void handleExpoPublished(ExpoPublishedRequest request) {
        log.info("expo-published received expoId={}", request.expoId());
        repository.save(ExpoTag.of(request.expoId(), "UNTAGGED", null, LocalDateTime.now()));
    }
}
