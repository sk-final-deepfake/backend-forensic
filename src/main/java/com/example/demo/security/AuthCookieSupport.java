package com.example.demo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// 리프레시 JWT를 HttpOnly Set-Cookie로 브라우저에 내려주거나, 로그아웃 시 즉시 만료시킨다.
// AuthController(로그인·재발급·로그아웃)에서 사용하고, application.yaml의 auth.cookie 설정을 읽는다.
@Component
public class AuthCookieSupport {

    @Value("${auth.cookie.name:refreshToken}")
    private String cookieName;

    @Value("${auth.cookie.path:/}")
    private String cookiePath;

    // 로컬: false(http), 운영: true(https ALB)
    @Value("${auth.cookie.secure:true}")
    private boolean secure;

    @Value("${auth.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${jwt.refresh-expiration-days:7}")
    private long refreshExpirationDays;

    // AuthController 재발급 API의 @CookieValue 이름과 맞추기 위한 쿠키명
    public String cookieName() {
        return cookieName;
    }

    // 로그인·재발급 성공 시 리프레시 JWT를 HttpOnly 쿠키로 설정
    public void addRefreshTokenCookie(jakarta.servlet.http.HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken, refreshMaxAgeSeconds()).toString());
    }

    // 로그아웃 시 Max-Age=0으로 리프레시 쿠키 삭제
    public void clearRefreshTokenCookie(jakarta.servlet.http.HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie("", 0).toString());
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();
    }

    private long refreshMaxAgeSeconds() {
        return refreshExpirationDays * 24 * 60 * 60;
    }
}
