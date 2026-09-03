package com.team1.expo.expo.controller;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.common.response.ApiResponse;
import com.team1.expo.expo.dto.ExpoPublicationResponse;
import com.team1.expo.expo.service.ExpoPublicationService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 박람회 공개 전환 API (P4, #24). 채널 소유자(ORGANIZER)만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/expos")
@RequiredArgsConstructor
public class ExpoPublicationController {

    private static final String ROLE_ORGANIZER = "ORGANIZER";

    private final ExpoPublicationService expoPublicationService;

    @PostMapping("/{expoId}/publication")
    public ApiResponse<ExpoPublicationResponse> publishExpo(@PathVariable Long expoId) {
        AuthenticatedUser user = requireOrganizer();
        return ApiResponse.ok(expoPublicationService.publish(expoId, user.userId()));
    }

    /*
     * common-security의 JwtAuthenticationFilter는 토큰이 없으면 그냥 통과시키므로,
     * 인증·권한은 여기서 직접 확인한다. (identity/expo의 기존 컨트롤러와 동일한 방식)
     */
    private AuthenticatedUser requireOrganizer() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (!ROLE_ORGANIZER.equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
