package com.team1.identity.auth;

import com.team1.identity.auth.dto.LoginRequest;
import com.team1.identity.auth.dto.LoginResponse;
import com.team1.identity.auth.dto.SignUpRequest;
import com.team1.identity.auth.dto.SignUpResponse;
import com.team1.identity.auth.service.AuthService;
import com.team1.identity.common.exception.BusinessException;
import com.team1.identity.common.exception.ErrorCode;
import com.team1.identity.support.IntegrationTestSupport;
import com.team1.security.AuthenticatedUser;
import com.team1.security.JwtValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginServiceTest extends IntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("로그인 토큰은 common-security의 JwtValidator로 검증되며 userId와 role을 담고 있다")
    void 발급_토큰이_공통_라이브러리로_검증된다() {
        String email = uniqueEmail();
        SignUpResponse signedUp = authService.signUp(new SignUpRequest(email, "password123", "테스터"));

        LoginResponse response = authService.login(new LoginRequest(email, "password123"));

        // 다른 Service가 실제로 하는 것과 똑같은 검증을 수행한다.
        AuthenticatedUser authenticated = new JwtValidator(TEST_JWT_SECRET)
                .validate(response.accessToken());

        assertThat(authenticated.userId()).isEqualTo(signedUp.userId());
        assertThat(authenticated.role()).isEqualTo("USER");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("Access Token의 만료는 발급 후 1시간이다")
    void 토큰_만료는_1시간() {
        String email = uniqueEmail();
        authService.signUp(new SignUpRequest(email, "password123", "테스터"));

        Instant before = Instant.now();
        LoginResponse response = authService.login(new LoginRequest(email, "password123"));

        assertThat(Duration.between(before, response.expiresAt()))
                .isBetween(Duration.ofMinutes(59), Duration.ofMinutes(61));
    }

    @Test
    @DisplayName("존재하지 않는 이메일과 틀린 비밀번호는 응답으로 구분할 수 없다")
    void 로그인_실패는_구분되지_않는다() {
        String email = uniqueEmail();
        authService.signUp(new SignUpRequest(email, "password123", "테스터"));

        BusinessException wrongPassword = catchBusinessException(
                () -> authService.login(new LoginRequest(email, "wrongpassword1")));
        BusinessException unknownEmail = catchBusinessException(
                () -> authService.login(new LoginRequest(uniqueEmail(), "password123")));

        assertThat(wrongPassword.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(unknownEmail.getErrorCode()).isEqualTo(wrongPassword.getErrorCode());
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    private BusinessException catchBusinessException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("BusinessException이 발생하지 않았습니다");
        } catch (BusinessException e) {
            return e;
        }
    }

    @Test
    @DisplayName("가입하지 않은 이메일로 로그인하면 INVALID_CREDENTIALS")
    void 미가입_로그인() {
        assertThatThrownBy(() -> authService.login(new LoginRequest(uniqueEmail(), "password123")))
                .isInstanceOf(BusinessException.class);
    }
}
