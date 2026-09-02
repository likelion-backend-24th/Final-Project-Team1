package com.team1.reservation.common;


public record ApiResponse<T>(boolean success, T data, Object meta, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Object meta) {
        return new ApiResponse<>(true, data, meta, null);
    }

    public static ApiResponse<ErrorBody> fail(ErrorCode code, String traceId, String message) {
        return new ApiResponse<>(false, new ErrorBody(code.name()), new TraceMeta(traceId), message);
    }

    public record ErrorBody(String code) {
    }

    public record TraceMeta(String traceId) {
    }
}
