package com.team1.identity.organizer.service;

import com.team1.identity.common.exception.BusinessException;
import com.team1.identity.common.exception.ErrorCode;
import com.team1.identity.common.security.CurrentUser;
import com.team1.identity.organizer.dto.OrganizerApplicationResponse;
import com.team1.identity.organizer.entity.OrganizerApplication;
import com.team1.identity.organizer.entity.OrganizerApplicationStatus;
import com.team1.identity.organizer.repository.OrganizerApplicationRepository;
import com.team1.identity.user.entity.Role;
import com.team1.identity.user.entity.User;
import com.team1.identity.user.repository.UserRepository;
import com.team1.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizerApplicationService {

    private final OrganizerApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @Transactional
    public OrganizerApplicationResponse apply(String reason) {
        AuthenticatedUser current = CurrentUser.require();
        Long userId = current.userId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (user.primaryRole().ordinal() >= Role.ORGANIZER.ordinal()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        if (applicationRepository.existsByUserIdAndStatus(userId, OrganizerApplicationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        OrganizerApplication application = OrganizerApplication.create(userId, reason, LocalDateTime.now());
        return OrganizerApplicationResponse.from(applicationRepository.save(application));
    }

    public OrganizerApplicationResponse getMyApplication() {
        Long userId = CurrentUser.require().userId();
        OrganizerApplication application = applicationRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return OrganizerApplicationResponse.from(application);
    }

    public List<OrganizerApplicationResponse> listApplications() {
        CurrentUser.requireRole(Role.SUPER_ADMIN);
        return applicationRepository.findByStatusOrderByCreatedAtAsc(OrganizerApplicationStatus.PENDING)
                .stream()
                .map(a -> {
                    User applicant = userRepository.findById(a.getUserId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
                    return OrganizerApplicationResponse.from(a, applicant);
                })
                .toList();
    }

    @Transactional
    public OrganizerApplicationResponse approve(Long applicationId) {
        AuthenticatedUser admin = CurrentUser.requireRole(Role.SUPER_ADMIN);

        OrganizerApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (application.getStatus() != OrganizerApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        application.approve(admin.userId(), LocalDateTime.now());

        User user = userRepository.findById(application.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        user.addRole(Role.ORGANIZER, LocalDateTime.now());

        return OrganizerApplicationResponse.from(application);
    }

    @Transactional
    public OrganizerApplicationResponse reject(Long applicationId, String rejectReason) {
        AuthenticatedUser admin = CurrentUser.requireRole(Role.SUPER_ADMIN);

        OrganizerApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (application.getStatus() != OrganizerApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        application.reject(admin.userId(), rejectReason, LocalDateTime.now());
        return OrganizerApplicationResponse.from(application);
    }
}
