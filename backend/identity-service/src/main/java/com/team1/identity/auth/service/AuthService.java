package com.team1.identity.auth.service;

import com.team1.identity.auth.dto.LoginRequest;
import com.team1.identity.auth.dto.LoginResponse;
import com.team1.identity.auth.dto.SignUpRequest;
import com.team1.identity.auth.dto.SignUpResponse;
import com.team1.identity.auth.jwt.IssuedToken;
import com.team1.identity.auth.jwt.JwtTokenProvider;
import com.team1.identity.common.exception.BusinessException;
import com.team1.identity.common.exception.ErrorCode;
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
         */
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        IssuedToken token = jwtTokenProvider.issue(user.getId(), user.primaryRole());
        return new LoginResponse(token.accessToken(), "Bearer", token.expiresAt());
    }
}
