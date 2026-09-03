package com.team1.reservation.common;


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
