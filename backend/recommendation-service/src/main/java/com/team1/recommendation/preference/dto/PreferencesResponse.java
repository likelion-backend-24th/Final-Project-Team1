package com.team1.recommendation.preference.dto;

import java.util.List;

public record PreferencesResponse(List<String> categories, List<String> keywords) {}
