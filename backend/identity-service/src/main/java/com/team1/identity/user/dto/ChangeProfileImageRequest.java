package com.team1.identity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeProfileImageRequest(

        @NotBlank
        @Size(max = 500)
        String imageUrl
) {
}
