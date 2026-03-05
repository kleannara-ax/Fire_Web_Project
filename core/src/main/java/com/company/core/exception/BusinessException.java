package com.company.core.exception;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 로직 예외 기반 클래스
 * <p>
 * - 각 모듈에서 이 클래스를 상속하여 도메인 전용 예외를 정의
 * - GlobalExceptionHandler에서 일괄 처리
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public BusinessException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_REQUEST, cause);
    }

    public BusinessException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
