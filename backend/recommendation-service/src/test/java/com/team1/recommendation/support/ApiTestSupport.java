package com.team1.recommendation.support;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.HexFormat;

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

    static {
        byte[] keyBytes = HexFormat.of().parseHex(
                "48cb046985eb576b3360e3049e5fdb9b8849bac7b8aa9cefdfa8ec9ece8dd7cd");
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        Date farFuture = new Date(9_999_999_999_000L);

        USER_JWT = Jwts.builder()
                .subject("1")
                .claim("role", "USER")
                .expiration(farFuture)
                .signWith(key)
                .compact();

        ORGANIZER_JWT = Jwts.builder()
                .subject("2")
                .claim("role", "ORGANIZER")
                .expiration(farFuture)
                .signWith(key)
                .compact();
    }
}
