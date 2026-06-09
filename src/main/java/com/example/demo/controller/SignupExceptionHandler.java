package com.example.demo.controller;

import com.example.demo.dto.api.ApiErrorResponse;
import com.example.demo.exception.DuplicateSignupFieldException;
import com.example.demo.exception.InvalidInviteCodeException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice(assignableTypes = {
        AuthController.class,
        InviteCodeController.class,
        OrganizationController.class
})
public class SignupExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ApiErrorResponse.FieldErrorDetail> details = e.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> ApiErrorResponse.FieldErrorDetail.builder()
                        .field(error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName())
                        .reason(error.getDefaultMessage())
                        .build())
                .toList();

        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message("요청 값이 올바르지 않습니다.")
                .details(details)
                .build());
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message(e.getMessage())
                .details(List.of())
                .build());
    }

    @ExceptionHandler(DuplicateSignupFieldException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(DuplicateSignupFieldException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.builder()
                .error("DUPLICATE_RESOURCE")
                .message(e.getMessage())
                .details(List.of(ApiErrorResponse.FieldErrorDetail.builder()
                        .field(e.getField())
                        .reason(e.getMessage())
                        .build()))
                .build());
    }

    @ExceptionHandler(InvalidInviteCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidInviteCode(InvalidInviteCodeException e) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message(e.getMessage())
                .details(List.of(ApiErrorResponse.FieldErrorDetail.builder()
                        .field("inviteCode")
                        .reason(e.getMessage())
                        .build()))
                .build());
    }
}
