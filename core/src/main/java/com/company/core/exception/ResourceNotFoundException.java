package com.company.core.exception;

import org.springframework.http.HttpStatus;

/**
 * 리소스를 찾을 수 없는 경우 (HTTP 404)
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " (id=" + id + ")을(를) 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }
}
