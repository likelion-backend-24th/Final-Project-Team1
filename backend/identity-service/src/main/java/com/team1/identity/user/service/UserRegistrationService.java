package com.team1.identity.user.service;

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

/**
 * signUp과 createOrganizer는 부여하는 Role만 다르고 저장 절차가 같다.
 * 중복 방어와 Transaction 경계를 한 곳에서만 관리하기 위해 분리했다.
 */
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public User register(String email, String rawPassword, String name, Role role) {
        User user = User.create(
                email,
                passwordEncoder.encode(rawPassword),
                name,
                role,
                LocalDateTime.now(clock)
        );

        try {
            // 사전 조회가 아니라 UNIQUE 제약 위반을 잡는다. 동시 요청에도 한 건만 생성되게 하는 유일한 방법이다.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        return user;
    }
}
