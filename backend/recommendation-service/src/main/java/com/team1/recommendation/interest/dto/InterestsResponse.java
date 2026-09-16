package com.team1.recommendation.interest.dto;

import java.util.List;

public record InterestsResponse(List<String> categories, List<String> keywords) {}
