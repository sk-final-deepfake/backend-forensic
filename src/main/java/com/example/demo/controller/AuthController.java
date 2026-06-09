package com.example.demo.controller;

import com.example.demo.dto.signup.SignupRequest;
import com.example.demo.dto.signup.SignupResponse;
import com.example.demo.dto.signup.UsernameCheckResponse;
import com.example.demo.service.SignupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signupService.signup(request));
    }

    @GetMapping("/username/check")
    public UsernameCheckResponse checkUsername(
            @RequestParam @NotBlank(message = "로그인 아이디는 필수입니다.") String loginId
    ) {
        return UsernameCheckResponse.builder()
                .available(signupService.isLoginIdAvailable(loginId))
                .build();
    }
}
