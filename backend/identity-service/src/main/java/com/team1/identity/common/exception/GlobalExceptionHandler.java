package com.team1.identity.common.exception;

import com.team1.identity.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorData>> handleBusiness(BusinessException e) {
        return build(e.getErrorCode(), e.getErrorCode().getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorData>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        return build(ErrorCode.INVALID_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorData>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽을 수 없음: {}", e.getMessage());
        return build(ErrorCode.INVALID_REQUEST, "요청 본문을 읽을 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorData>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<ApiResponse.ErrorData>> build(ErrorCode errorCode, String message) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getStatus());
        if (errorCode.getStatus() == HttpStatus.UNAUTHORIZED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return builder.body(ApiResponse.error(errorCode.name(), message));
    }
}
