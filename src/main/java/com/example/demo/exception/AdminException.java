package com.example.demo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AdminException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AdminException(HttpStatus status, String message) {
        this(status, "ADMIN_ERROR", message);
    }

    public AdminException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
