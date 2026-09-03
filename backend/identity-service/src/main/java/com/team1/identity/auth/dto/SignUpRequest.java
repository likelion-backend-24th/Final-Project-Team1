package com.team1.identity.auth.dto;

import com.team1.identity.common.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(

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

    /* 검증(@Email 등)보다 먼저 실행되도록 compact 생성자에서 정규화한다. */
    public SignUpRequest {
        email = EmailNormalizer.normalize(email);
    }

    @Override
    public String toString() {
        return "SignUpRequest[email=" + email + ", name=" + name + "]";
    }
}
