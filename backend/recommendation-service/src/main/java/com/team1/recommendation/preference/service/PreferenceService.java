package com.team1.recommendation.preference.service;

import com.team1.recommendation.preference.dto.PreferencesResponse;
import com.team1.recommendation.preference.dto.UpsertPreferencesRequest;
import com.team1.recommendation.preference.entity.PreferenceType;
import com.team1.recommendation.preference.entity.UserPreference;
import com.team1.recommendation.preference.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PreferenceService {

    private final UserPreferenceRepository repository;

    public PreferenceService(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PreferencesResponse upsert(Long userId, UpsertPreferencesRequest request) {
        repository.deleteAllByUserId(userId);

        LocalDateTime now = LocalDateTime.now();
        List<String> categories = request.categories() == null ? List.of() : request.categories();
        List<String> keywords = request.keywords() == null ? List.of() : request.keywords();

        categories.forEach(c ->
                repository.save(UserPreference.of(userId, PreferenceType.CATEGORY, c, now)));
        keywords.forEach(k ->
                repository.save(UserPreference.of(userId, PreferenceType.KEYWORD, k, now)));

        return new PreferencesResponse(categories, keywords);
    }
}
