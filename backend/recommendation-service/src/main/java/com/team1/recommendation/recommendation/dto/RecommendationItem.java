package com.team1.recommendation.recommendation.dto;

import java.util.List;

public record RecommendationItem(Long expoId, String title, List<String> matchedTags, double score) {}
