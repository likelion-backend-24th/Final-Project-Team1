package com.team1.identity.admin.service;

import com.team1.identity.admin.dto.CreateOrganizerRequest;
import com.team1.identity.admin.dto.CreateOrganizerResponse;
import com.team1.identity.common.security.CurrentUser;
import com.team1.identity.user.entity.Role;
import com.team1.identity.user.entity.User;
import com.team1.identity.user.service.UserRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRegistrationService userRegistrationService;

    public CreateOrganizerResponse createOrganizer(CreateOrganizerRequest request) {
        // 권한 검사가 저장보다 반드시 먼저다. 403인 경우 사용자가 생성되면 안 된다.
        CurrentUser.requireRole(Role.SUPER_ADMIN);

        User user = userRegistrationService.register(
                request.email(), request.password(), request.name(), Role.ORGANIZER);

        return new CreateOrganizerResponse(
                user.getId(), user.getEmail(), user.getName(), Role.ORGANIZER.name());
    }
}
