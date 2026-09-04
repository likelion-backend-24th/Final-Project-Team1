package com.team1.identity.auth.service;

import com.team1.identity.auth.dto.LoginRequest;
import com.team1.identity.auth.dto.LoginResponse;
import com.team1.identity.auth.dto.SignUpRequest;
import com.team1.identity.auth.dto.SignUpResponse;
import com.team1.identity.auth.jwt.IssuedToken;
import com.team1.identity.auth.jwt.JwtTokenProvider;
import com.team1.identity.common.exception.BusinessException;
import com.team1.identity.common.exception.ErrorCode;
import com.team1.identity.common.util.EmailNormalizer;
import com.team1.identity.user.entity.Role;
import com.team1.identity.user.entity.User;
import com.team1.identity.user.repository.UserRepository;
import com.team1.identity.user.service.UserRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    /*
     * 존재하지 않는 이메일로 로그인해도 Hash 비교를 한 번은 수행하기 위한 더미 Hash다.
     * 실제 사용자의 Hash와 같은 work factor(12)여야 걸리는 시간이 같아진다.
     * 이 Hash에 대응하는 계정은 없으므로 이 값으로 로그인할 수 있는 사용자는 존재하지 않는다.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$xtlew4uuJuhTgLn55.5hl.7WZcZ1FZ8xHglmBrAICjFRun3ZJLKWu";

    private final UserRegistrationService userRegistrationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public SignUpResponse signUp(SignUpRequest request) {
        User user = userRegistrationService.register(
                request.email(), request.password(), request.name(), Role.USER);

        return new SignUpResponse(user.getId(), user.getEmail(), user.getName(), Role.USER.name());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        /*
         * 존재하지 않는 이메일과 틀린 비밀번호는 같은 예외 · 같은 코드 · 같은 메시지여야 한다.
         * 응답이 달라지면 공격자가 어떤 이메일이 가입돼 있는지 알아낼 수 있다.
         *
         * 본문뿐 아니라 걸리는 시간도 같아야 한다. 사용자를 못 찾았다고 바로 예외를 던지면
         * BCrypt(work factor 12, 수백 ms) 비교를 건너뛰게 되어, 응답 본문이 같아도
         * 응답 시간만 재면 가입 여부를 알 수 있다. 그래서 못 찾은 경우에도 더미 Hash로
         * 비교를 한 번 수행한 뒤 같은 예외를 던진다.
         */
        User user = userRepository.findByEmail(EmailNormalizer.normalize(request.email()))
                .orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        IssuedToken token = jwtTokenProvider.issue(user.getId(), user.primaryRole());
        return new LoginResponse(token.accessToken(), "Bearer", token.expiresAt());
    }
}
