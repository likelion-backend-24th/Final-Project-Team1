package com.team1.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedUser user = jwtValidator.validate(header.substring(7));
            AuthContext.set(user);
            chain.doFilter(request, response);
        } catch (InvalidTokenException e) {
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"success\":false,\"data\":{\"code\":\"UNAUTHENTICATED\"},\"meta\":null,\"message\":\"인증이 필요합니다\"}"
            );
        } finally {
            AuthContext.clear();
        }
    }
}
