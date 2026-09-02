package com.team1.identity.auth.jwt;

import java.time.Instant;

public record IssuedToken(String accessToken, Instant expiresAt) {
}
