package com.team1.reservation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.common.TraceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


@Component
@Order(2)
public class InternalAuthFilter extends OncePerRequestFilter {

    private final byte[] expected;
    private final ObjectMapper objectMapper;

    public InternalAuthFilter(@Value("${internal.token}") String token, ObjectMapper objectMapper) {
        this.expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8), expected)) {
            response.setStatus(ErrorCode.UNAUTHENTICATED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.fail(ErrorCode.UNAUTHENTICATED, TraceId.get(), "internal token mismatch"));
            return;
        }

        chain.doFilter(request, response);
    }
}
