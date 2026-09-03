package com.team1.reservation.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorBody>> handleApi(ApiException e) {
        return build(e.code(), e.getMessage());
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorBody>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        return build(ErrorCode.INVALID_REQUEST, message.isBlank() ? "invalid request" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorBody>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("malformed request body traceId={}", TraceId.get(), e);
        return build(ErrorCode.INVALID_REQUEST, "malformed request body");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorBody>> handleMissingParam(
            MissingServletRequestParameterException e) {
        return build(ErrorCode.INVALID_REQUEST, "missing parameter: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorBody>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return build(ErrorCode.INVALID_REQUEST, "invalid value for parameter: " + e.getName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorBody>> handleUnexpected(Exception e) {
        // 내부 예외 내용은 Log 에만 남기고 응답에는 노출하지 않는다.
        log.error("unhandled exception traceId={}", TraceId.get(), e);
        return build(ErrorCode.INTERNAL_ERROR, "unexpected error");
    }

    private ResponseEntity<ApiResponse<ApiResponse.ErrorBody>> build(ErrorCode code, String message) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status());
        if (code == ErrorCode.UNAUTHENTICATED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return builder.body(ApiResponse.fail(code, TraceId.get(), message));
    }
}
