package com.team1.recommendation.expo.service;

import com.team1.recommendation.expo.dto.SimilarExposResponse;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SimilarExpoService {

    private final ExpoTagRepository repository;

    public SimilarExpoService(ExpoTagRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SimilarExposResponse findSimilar(Long expoId) {
        List<String> tags = repository.findByExpoId(expoId).stream()
                .map(t -> t.getTagValue())
                .filter(v -> !"UNTAGGED".equals(v))
                .toList();

        if (tags.isEmpty()) {
            return new SimilarExposResponse(List.of());
        }

        return new SimilarExposResponse(repository.findSimilarExpoIds(expoId, tags));
    }
}
