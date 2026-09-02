package com.team1.identity.auth.service;

import com.team1.identity.auth.dto.SignUpRequest;
import com.team1.identity.auth.dto.SignUpResponse;
import com.team1.identity.common.exception.BusinessException;
import com.team1.identity.common.exception.ErrorCode;
import com.team1.identity.user.entity.Role;
import com.team1.identity.user.entity.User;
import com.team1.identity.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        return createUser(request.email(), request.password(), request.name(), Role.USER);
    }

    SignUpResponse createUser(String email, String rawPassword, String name, Role role) {
        User user = User.create(
                email,
                passwordEncoder.encode(rawPassword),
                name,
                role,
                LocalDateTime.now(clock)
        );

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        return new SignUpResponse(user.getId(), user.getEmail(), user.getName(), role.name());
    }
}
