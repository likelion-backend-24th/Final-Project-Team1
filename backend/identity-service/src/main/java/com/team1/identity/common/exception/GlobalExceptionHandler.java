package com.team1.identity.common.exception;

import com.team1.identity.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
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

    /*
     * Spring MVC가 던지는 클라이언트 오류(경로 없음 404 · 메서드 불일치 405 ·
     * Content-Type 불일치 415 등)는 모두 ErrorResponse를 구현한다.
     * 이것을 먼저 걸러내지 않으면 아래 500으로 뭉개져서 두 가지 문제가 생긴다.
     *   - 클라이언트가 "내 요청이 잘못됨"과 "서버 장애"를 구분할 수 없다.
     *   - 오타 URL이나 Bot 스캔 한 건마다 Stack Trace가 error Log에 쌓인다.
     *
     * 상태 코드는 예외가 가진 값(405 · 415 …)을 그대로 쓰고, code 필드는 계약에 이미
     * 있는 값만 사용한다. 405 · 415 전용 code가 필요해지면 그때 팀 계약에 추가한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ApiResponse.ErrorData>> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            ErrorCode errorCode = status.value() == HttpStatus.NOT_FOUND.value()
                    ? ErrorCode.NOT_FOUND
                    : ErrorCode.INVALID_REQUEST;

            log.warn("클라이언트 오류 status={} {}", status.value(), e.getMessage());
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(errorCode.name(), errorCode.getMessage()));
        }

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
