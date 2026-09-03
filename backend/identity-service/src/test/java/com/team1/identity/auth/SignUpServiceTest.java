package com.team1.identity.auth;

import com.team1.identity.auth.dto.SignUpRequest;
import com.team1.identity.auth.dto.SignUpResponse;
import com.team1.identity.auth.service.AuthService;
import com.team1.identity.common.exception.BusinessException;
import com.team1.identity.common.exception.ErrorCode;
import com.team1.identity.support.IntegrationTestSupport;
import com.team1.identity.user.entity.Role;
import com.team1.identity.user.entity.User;
import com.team1.identity.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignUpServiceTest extends IntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("가입에 성공하면 사용자와 USER Role이 한 Transaction에 저장된다")
    void 가입_성공() {
        String email = uniqueEmail();

        SignUpResponse response = authService.signUp(new SignUpRequest(email, "password123", "테스터"));

        assertThat(response.userId()).isNotNull();
        assertThat(response.role()).isEqualTo("USER");

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getRoles()).hasSize(1);
        assertThat(saved.primaryRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("비밀번호는 원문이 아니라 BCrypt(work factor 12) 해시로 저장된다")
    void 비밀번호_해시_저장() {
        String email = uniqueEmail();
        authService.signUp(new SignUpRequest(email, "password123", "테스터"));

        User saved = userRepository.findByEmail(email).orElseThrow();

        assertThat(saved.getPasswordHash())
                .isNotEqualTo("password123")
                .startsWith("$2a$12$")
                .hasSize(60);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 DUPLICATE_EMAIL이고 사용자가 추가로 생성되지 않는다")
    void 이메일_중복() {
        String email = uniqueEmail();
        authService.signUp(new SignUpRequest(email, "password123", "테스터"));

        assertThatThrownBy(() -> authService.signUp(new SignUpRequest(email, "password123", "다른사람")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        assertThat(countByEmail(email)).isEqualTo(1);
    }

    private long countByEmail(String email) {
        return userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(email))
                .count();
    }
}
