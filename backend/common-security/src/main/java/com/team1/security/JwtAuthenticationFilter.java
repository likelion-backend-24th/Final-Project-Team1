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
        } catch (InvalidTokenException e) {
            // 헤더가 없을 때와 동일하게 다룬다: 여기서 바로 거절하면 로그인처럼
            // 인증이 필요 없는 요청까지, 브라우저에 남아있는 만료된 Token 때문에 막혀버린다.
            // 실제로 보호가 필요한 기능은 각 서비스가 AuthContext 유무를 직접 검사해서 거절한다.
        }

        try {
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }
}
