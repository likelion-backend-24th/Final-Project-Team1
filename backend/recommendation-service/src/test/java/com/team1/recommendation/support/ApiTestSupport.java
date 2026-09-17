package com.team1.recommendation.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.util.Date;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    protected static final String INTERNAL_TOKEN = "test-internal-token";
    protected static final String USER_JWT;
    protected static final String ORGANIZER_JWT;

    private static final String TEST_JWT_SECRET =
            "48cb046985eb576b3360e3049e5fdb9b8849bac7b8aa9cefdfa8ec9ece8dd7cd";

    static {
        USER_JWT = userJwt(1L);

        ORGANIZER_JWT = jwt(2L, "ORGANIZER");
    }

    /** 테스트끼리 DB 를 공유하므로, 데이터를 쌓는 테스트는 고유한 회원 ID 를 쓴다. */
    protected static String userJwt(long userId) {
        return jwt(userId, "USER");
    }

    private static String jwt(long userId, String role) {
        // JwtValidator.java와 동일하게 secret.getBytes()로 키 생성
        SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .expiration(new Date(9_999_999_999_000L))
                .signWith(key)
                .compact();
    }
}
