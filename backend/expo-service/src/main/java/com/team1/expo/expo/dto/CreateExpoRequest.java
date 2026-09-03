package com.team1.expo.expo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateExpoRequest(
        @NotNull Long channelId,
        @NotBlank @Size(max = 200) String title,
        String description,
        @Size(max = 200) String venue,
        @Size(max = 50) String region,
        @NotBlank @Size(max = 50) String category,
        @Size(max = 500) String thumbnailUrl
) {}
