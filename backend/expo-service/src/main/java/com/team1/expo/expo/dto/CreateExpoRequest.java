package com.team1.expo.expo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateExpoRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        @Size(max = 200) String venue,
        @Size(max = 50) String region,
        @NotBlank @Pattern(regexp = "IT·전자|식품·음료|패션·뷰티|교육·취업|문화·예술|기타") String category,
        @Size(max = 500) String thumbnailUrl
) {}
