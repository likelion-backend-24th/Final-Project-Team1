package com.team1.expo.common;

/**
 * 요청 전 구간에서 X-Trace-Id를 전달하기 위한 ThreadLocal 보관소.
 * TraceIdFilter가 유입 헤더를 넣고, 내부 서비스 호출 시 그대로 다시 실어 보낸다.
 */
public final class TraceId {

    public static final String HEADER = "X-Trace-Id";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceId() {
    }

    public static void set(String traceId) {
        CURRENT.set(traceId);
    }

    public static String get() {
        String value = CURRENT.get();
        return value == null ? "-" : value;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
