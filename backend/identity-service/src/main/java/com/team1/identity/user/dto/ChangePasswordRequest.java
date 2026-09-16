package com.team1.identity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(

        @NotBlank
        String currentPassword,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "비밀번호는 8~64자이며 영문과 숫자를 모두 포함해야 합니다."
        )
        String newPassword
) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[]";
    }
}
