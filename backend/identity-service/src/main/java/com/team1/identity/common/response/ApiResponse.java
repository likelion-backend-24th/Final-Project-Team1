package com.team1.identity.common.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        Object meta,
        String message
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Object meta) {
        return new ApiResponse<>(true, data, meta, null);
    }

    public static ApiResponse<ErrorData> error(String code, String message) {
        return new ApiResponse<>(false, new ErrorData(code), null, message);
    }

    public record ErrorData(String code) {
    }
}
