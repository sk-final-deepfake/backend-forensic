package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SecurityCheckDto {

    private String checkType;
    private boolean valid;
    private String errorCode;
    private String message;
}
