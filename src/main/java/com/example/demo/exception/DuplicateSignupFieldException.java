package com.example.demo.exception;

import lombok.Getter;

@Getter
public class DuplicateSignupFieldException extends RuntimeException {

    private final String field;

    public DuplicateSignupFieldException(String field, String message) {
        super(message);
        this.field = field;
    }
}
