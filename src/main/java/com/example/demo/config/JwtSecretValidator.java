package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class JwtSecretValidator {

    private static final int MIN_SECRET_BYTES = 32;
    private static final Set<String> INSECURE_SECRETS = Set.of(
            "forenshield-dev-jwt-secret-key-min-32-chars",
            "forenshield-local-dev-jwt-secret-key-min-32-chars",
            "YOUR_JWT_HEX_SIGNING_KEY_DO_NOT_LEAK",
            "test-jwt-secret-key-for-unit-tests-only"
    );

    private final JwtProperties jwtProperties;
    private final Environment environment;

    @jakarta.annotation.PostConstruct
    void validate() {
        String secret = jwtProperties.getSecret();
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET_KEY 환경 변수가 설정되지 않았습니다. 운영/로컬 모두 jwt.secret 값이 필요합니다."
            );
        }

        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET_KEY는 HS256 서명을 위해 최소 32바이트(문자) 이상이어야 합니다."
            );
        }

        if (INSECURE_SECRETS.contains(secret) || secret.startsWith("YOUR_")) {
            String message = "JWT_SECRET_KEY에 예시/기본 placeholder 값이 설정되어 있습니다. "
                    + "운영용 랜덤 시크릿으로 교체하세요.";
            if (isProd) {
                throw new IllegalStateException(message);
            }
        }
    }
}
