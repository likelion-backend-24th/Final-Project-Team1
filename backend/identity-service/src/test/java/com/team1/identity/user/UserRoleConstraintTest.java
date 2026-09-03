package com.team1.identity.user;

import com.team1.identity.support.IntegrationTestSupport;
import com.team1.identity.user.entity.Role;
import com.team1.identity.user.entity.User;
import com.team1.identity.user.service.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Role 제한은 Java enum과 DB CHECK 제약 두 겹이다.
 * 애플리케이션을 거치지 않는 직접 INSERT를 DB가 막는지 확인한다.
 */
class UserRoleConstraintTest extends IntegrationTestSupport {

    @Autowired
    private UserRegistrationService userRegistrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("정의되지 않은 Role 값은 DB가 거절한다")
    void 허용되지_않은_Role() {
        Long userId = newUserId();

        /*
         * MySQL의 CHECK 위반은 error code 3819이고, Spring은 이 번호를
         * DataIntegrityViolationException으로 분류하지 못해 UncategorizedSQLException으로 넘긴다.
         * (UNIQUE 위반 1062는 분류된다) 따라서 상위 타입과 메시지로 확인한다.
         */
        assertThatThrownBy(() -> insertRole(userId, "ADMIN"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_user_roles_role");
    }

    @Test
    @DisplayName("정의된 Role 값은 정상 저장된다")
    void 허용된_Role() {
        Long userId = newUserId();

        assertThatCode(() -> insertRole(userId, "ORGANIZER")).doesNotThrowAnyException();
    }

    private Long newUserId() {
        User user = userRegistrationService.register(
                uniqueEmail(), "password123", "제약테스트", Role.USER);
        return user.getId();
    }

    private void insertRole(Long userId, String role) {
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role, granted_at) VALUES (?, ?, UTC_TIMESTAMP())",
                userId, role);
    }
}
