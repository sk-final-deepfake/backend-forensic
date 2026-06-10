package com.example.demo.dto.api;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ApiErrorResponse {

    private String error;
    private String message;
    private List<FieldErrorDetail> details;

    @Getter
    @Builder
    public static class FieldErrorDetail {
        private String field;
        private String reason;
    }
}
