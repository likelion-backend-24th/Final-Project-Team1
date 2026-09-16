package com.team1.recommendation.preference.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UpsertPreferencesRequest(
        @Size(max = 6) List<String> categories,
        @Size(max = 30) List<String> keywords
) {}
