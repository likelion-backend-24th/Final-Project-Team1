package com.team1.recommendation.common;

public final class TraceId {
    public static final String HEADER = "X-Trace-Id";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceId() {}

    public static void set(String traceId) { CURRENT.set(traceId); }
    public static String get() { String v = CURRENT.get(); return v == null ? "-" : v; }
    public static void clear() { CURRENT.remove(); }
}
