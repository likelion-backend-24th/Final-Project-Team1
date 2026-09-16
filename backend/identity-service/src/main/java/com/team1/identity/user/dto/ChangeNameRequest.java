package com.team1.identity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeNameRequest(

        @NotBlank
        @Size(max = 100)
        String name
) {
}
