package com.team1.expo.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.util.Date;
import java.util.UUID;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=test"
)
public abstract class IntegrationTestSupport {

    protected static final String TEST_JWT_SECRET =
            "test-secret-0123456789abcdef0123456789abcdef0123456789abcdef";
    protected static final String TEST_INTERNAL_TOKEN = "test-internal-token";

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("expo");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("internal.token", () -> TEST_INTERNAL_TOKEN);
    }

    protected static String jwtFor(long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes()))
                .compact();
    }

    protected static String uniqueName() {
        return "channel-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
