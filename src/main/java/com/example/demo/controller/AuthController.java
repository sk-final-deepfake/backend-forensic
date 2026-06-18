package com.example.demo.controller;

import com.example.demo.dto.AuthenticatedTokens;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.signup.SignupRequest;
import com.example.demo.dto.signup.SignupResponse;
import com.example.demo.dto.signup.UsernameCheckResponse;
import com.example.demo.security.AuthCookieSupport;
import com.example.demo.service.AuthService;
import com.example.demo.service.SignupService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 인증 API — 로그인·재발급·로그아웃·회원가입.
// 리프레시 JWT는 AuthCookieSupport(HttpOnly 쿠키), 액세스 JWT는 LoginResponse 본문으로 내려준다.
@Validated
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;
    private final AuthCookieSupport authCookieSupport;

    // 액세스 JWT는 JSON, 리프레시 JWT는 Set-Cookie
    @PostMapping("/api/auth/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedTokens result = authService.login(request);
        authCookieSupport.addRefreshTokenCookie(response, result.tokens().getRefreshToken());
        return ResponseEntity.ok(LoginResponse.from(result.user(), result.tokens()));
    }

    // 브라우저 쿠키의 리프레시 JWT로 액세스 JWT 재발급 (인증 불필요)
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        AuthenticatedTokens result = authService.reissue(refreshToken);
        authCookieSupport.addRefreshTokenCookie(response, result.tokens().getRefreshToken());
        return ResponseEntity.ok(LoginResponse.from(result.user(), result.tokens()));
    }

    // Redis 리프레시 삭제 + 쿠키 만료 (204)
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(authorization, refreshToken);
        authCookieSupport.clearRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signupService.signup(request));
    }

    @GetMapping("/api/v1/auth/username/check")
    public UsernameCheckResponse checkUsername(
            @RequestParam @NotBlank(message = "로그인 아이디는 필수입니다.") String loginId
    ) {
        return UsernameCheckResponse.builder()
                .available(signupService.isLoginIdAvailable(loginId))
                .build();
    }
}
