package com.team1.identity.admin.dto;

import com.team1.identity.common.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizerRequest(

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "비밀번호는 8~64자이며 영문과 숫자를 모두 포함해야 합니다."
        )
        String password,

        @NotBlank
        @Size(max = 100)
        String name
) {

    public CreateOrganizerRequest {
        email = EmailNormalizer.normalize(email);
    }

    @Override
    public String toString() {
        return "CreateOrganizerRequest[email=" + email + ", name=" + name + "]";
    }
}
