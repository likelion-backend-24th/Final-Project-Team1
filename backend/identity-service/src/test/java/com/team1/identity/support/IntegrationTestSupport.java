package com.team1.identity.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.util.UUID;

/**
 * 모든 통합 Test의 공통 기반.
 *
 * 동시 가입 방어는 MySQL의 UNIQUE 제약이 담당하므로 H2로는 검증이 불가능하다.
 * Testcontainers로 실제 MySQL을 띄우고, Flyway가 그 위에 스키마를 만든다.
 * 컨테이너는 static 블록에서 한 번만 띄워 모든 Test 클래스가 공유한다(속도).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=test"
)
public abstract class IntegrationTestSupport {

    protected static final String TEST_JWT_SECRET =
            "test-secret-0123456789abcdef0123456789abcdef0123456789abcdef";

    /** SuperAdmin123! 의 BCrypt(work factor 12) 해시 */
    protected static final String SEED_PASSWORD = "SuperAdmin123!";
    protected static final String SEED_EMAIL = "admin@team1.local";
    private static final String SEED_PASSWORD_HASH =
            "$2a$12$T1OrTODtKp.xHMSuWN4o9enQusIrD/Ol0b.qEd3ZQUxkJXxkKY4lS";

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("identity");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("spring.flyway.placeholders.superAdminEmail", () -> SEED_EMAIL);
        registry.add("spring.flyway.placeholders.superAdminPasswordHash", () -> SEED_PASSWORD_HASH);
    }

    /** 컨테이너를 Test 클래스끼리 공유하므로 이메일이 겹치지 않게 매번 새로 만든다. */
    protected static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@team1.local";
    }
}
