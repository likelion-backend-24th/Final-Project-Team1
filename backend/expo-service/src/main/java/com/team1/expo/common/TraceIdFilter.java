package com.team1.expo.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Nginx가 실어 준 X-Trace-Id를 ThreadLocal에 담아, 내부 서비스 호출까지 이어지게 한다.
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceId.HEADER);
        if (traceId != null && !traceId.isBlank()) {
            TraceId.set(traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TraceId.clear();
        }
    }
}
