package com.example.demo.config;

import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.RestAccessDeniedHandler;
import com.example.demo.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",  // 비활성화 시 401 — 쿠키 기반 자동 재발급 차단
                                "/api/auth/logout",   // 쿠키·선택적 Bearer로 리프레시 무효화
                                "/api/v1/auth/signup",
                                "/api/v1/auth/username/check",
                                "/api/v1/invite-codes/validate",
                                "/api/v1/organizations/**",
                                "/api/v1/public/**",
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // 시스템 관리자 전용
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "ORG_ADMIN")
                        // 일반 사용자 화면(/main 등)용 API — ORG_ADMIN 차단 (관리자는 /api/v1/admin/** 사용)
                        .requestMatchers(
                                "/api/v1/evidences/dashboard/**",
                                "/api/v1/evidences/stats",
                                "/api/v1/evidences/stats/**",
                                "/api/v1/mypage/**",
                                "/api/v1/compare/**"
                        ).hasAnyRole("INVESTIGATOR", "USER", "REVIEWER")
                        .requestMatchers("/api/v1/evidences/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
