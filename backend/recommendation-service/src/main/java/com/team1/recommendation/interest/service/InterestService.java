package com.team1.recommendation.interest.service;

import com.team1.recommendation.interest.dto.InterestsResponse;
import com.team1.recommendation.interest.dto.UpsertInterestsRequest;
import com.team1.recommendation.interest.entity.InterestType;
import com.team1.recommendation.interest.entity.UserInterest;
import com.team1.recommendation.interest.repository.UserInterestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterestService {

    private final UserInterestRepository repository;

    public InterestService(UserInterestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public InterestsResponse upsert(Long userId, UpsertInterestsRequest request) {
        repository.deleteAllByUserId(userId);

        LocalDateTime now = LocalDateTime.now();
        List<String> categories = request.categories() == null ? List.of() : request.categories();
        List<String> keywords = request.keywords() == null ? List.of() : request.keywords();

        categories.forEach(c ->
                repository.save(UserInterest.of(userId, InterestType.CATEGORY, c, now)));
        keywords.forEach(k ->
                repository.save(UserInterest.of(userId, InterestType.KEYWORD, k, now)));

        return new InterestsResponse(categories, keywords);
    }
}
