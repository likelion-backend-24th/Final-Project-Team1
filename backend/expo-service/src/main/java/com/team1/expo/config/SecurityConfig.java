package com.team1.expo.config;

import com.team1.security.JwtAuthenticationFilter;
import com.team1.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter(@Value("${jwt.secret}") String secret) {
        FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtAuthenticationFilter(new JwtValidator(secret)));
        bean.addUrlPatterns("/*");
        return bean;
    }
}
