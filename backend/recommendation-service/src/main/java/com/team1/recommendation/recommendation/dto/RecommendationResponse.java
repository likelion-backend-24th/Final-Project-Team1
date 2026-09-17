package com.team1.recommendation.recommendation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendationResponse(List<RecommendationItem> recommendations, LocalDateTime generatedAt) {}
