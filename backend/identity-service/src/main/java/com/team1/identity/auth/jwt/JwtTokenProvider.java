package com.team1.identity.auth.jwt;

import com.team1.identity.user.entity.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMillis;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long expirationMillis,
            Clock clock) {

        // common-security의 JwtValidator와 동일한 방식으로 키를 만든다. 하나라도 다르면 서명이 맞지 않는다.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
        this.clock = clock;
    }

    public IssuedToken issue(Long userId, Role role) {
        /*
         * JWT의 iat·exp는 초 단위 정수다(RFC 7519). 나노초를 그대로 두면
         * 응답의 expiresAt과 Token의 exp가 최대 1초 어긋나므로 미리 잘라낸다.
         */
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusMillis(expirationMillis);

        String token = Jwts.builder()
                .subject(String.valueOf(userId))   // JwtValidator가 여기서 userId를 읽는다
                .claim("userId", userId)           // 계약서 표기를 맞추기 위한 중복 표기
                .claim("role", role.name())        // JwtValidator가 String으로 읽는다
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new IssuedToken(token, expiresAt);
    }
}
