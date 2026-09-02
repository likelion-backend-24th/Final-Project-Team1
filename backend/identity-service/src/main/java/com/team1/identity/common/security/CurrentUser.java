package com.team1.identity.common.security;

import com.team1.identity.common.exception.BusinessException;
import com.team1.identity.common.exception.ErrorCode;
import com.team1.identity.user.entity.Role;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;

public final class CurrentUser {

    private CurrentUser() {
    }

    /*
     * common-security의 JwtAuthenticationFilter는 Authorization 헤더가 없으면 그냥 통과시킨다.
     * 따라서 인증이 필요한 기능은 반드시 이 검사를 직접 해야 한다.
     */
    public static AuthenticatedUser require() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    public static AuthenticatedUser requireRole(Role required) {
        AuthenticatedUser user = require();
        if (!required.name().equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
