package com.team1.reservation.config;

import com.team1.security.JwtAuthenticationFilter;
import com.team1.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * common-security 의 JWT Filter 등록.
 * Filter 는 Token 이 없으면 AuthContext 를 비운 채 통과시키므로,
 * 401/403 판단과 응답 형식은 Controller + GlobalExceptionHandler 가 맡는다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            @Value("${jwt.secret}") String secret) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new JwtAuthenticationFilter(new JwtValidator(secret)));

        // 반드시 /api/* 로만 건다.
        // /internal/** 은 JWT 가 아니라 Service 간 Bearer Token 을 쓰는데,
        // 여기에 JWT Filter 가 걸리면 그 Token 을 JWT 로 파싱하려다 401 이 난다.
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);   // TraceIdFilter(1) 다음

        return registration;
    }
}
