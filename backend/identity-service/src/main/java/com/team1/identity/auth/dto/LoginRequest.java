package com.team1.identity.auth.dto;

import com.team1.identity.common.util.EmailNormalizer;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank
        String email,

        @NotBlank
        String password
) {

    /* 가입 때와 같은 규칙으로 정규화해야 대소문자가 달라도 로그인된다. */
    public LoginRequest {
        email = EmailNormalizer.normalize(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
