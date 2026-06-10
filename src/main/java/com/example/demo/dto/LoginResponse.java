package com.example.demo.dto;

import com.example.demo.domain.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private final boolean success;
    private final String token;
    private final Long userId;
    private final String loginId;
    private final String name;
    private final UserRole role;
}
