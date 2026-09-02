package com.team1.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

public class JwtValidator {

    private final SecretKey secretKey;

    public JwtValidator(String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public AuthenticatedUser validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            return new AuthenticatedUser(userId, role);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("토큰이 유효하지 않습니다", e);
        }
    }
}