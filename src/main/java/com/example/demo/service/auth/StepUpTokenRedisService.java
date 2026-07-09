package com.example.demo.service.auth;

import com.example.demo.config.JwtProperties;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Step-up 재인증 opaque 토큰 저장.
 * Redis 키: STEPUP:{token} → userId (RefreshTokenRedisService와 동일 패턴, Redis + 인메모리 폴백).
 */
@Service
public class StepUpTokenRedisService {

    private static final String KEY_PREFIX = "STEPUP:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final Map<String, Long> memoryStore = new ConcurrentHashMap<>();

    @Autowired
    public StepUpTokenRedisService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            JwtProperties jwtProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        String redisKey = key(token);
        String userIdValue = String.valueOf(userId);
        long ttlMinutes = jwtProperties.resolveStepUpExpirationMinutes();

        if (useRedis()) {
            try {
                redisTemplate.opsForValue().set(redisKey, userIdValue, ttlMinutes, TimeUnit.MINUTES);
                return token;
            } catch (RuntimeException ignored) {
                // Redis 미기동(로컬·테스트) 시 인메모리로 폴백
            }
        }

        memoryStore.put(redisKey, userId);
        return token;
    }

    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String redisKey = key(token);
        if (useRedis()) {
            try {
                String userId = redisTemplate.opsForValue().get(redisKey);
                if (userId != null) {
                    return Long.parseLong(userId);
                }
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리 조회
            }
        }

        return memoryStore.get(redisKey);
    }

    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        String redisKey = key(token);
        if (useRedis()) {
            try {
                redisTemplate.delete(redisKey);
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리만 삭제
            }
        }
        memoryStore.remove(redisKey);
    }

    private boolean useRedis() {
        return redisTemplate != null;
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
