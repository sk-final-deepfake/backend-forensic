package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 리프레시 JWT 자동 재발급 정책.
 * false이면 로그인 쿠키·/api/auth/refresh 재발급을 막아 URL 직접 접속 시 자동 로그인을 방지한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.refresh")
public class AuthRefreshProperties {

    private boolean enabled = false;
}
