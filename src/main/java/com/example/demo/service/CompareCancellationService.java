package com.example.demo.service;

import com.example.demo.domain.User;
import com.example.demo.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CompareCancellationService {

    private static final Duration CANCEL_TOKEN_TTL = Duration.ofMinutes(30);

    private final Map<String, Instant> cancelledRequests = new ConcurrentHashMap<>();

    public void cancel(User user, String requestId) {
        String key = requireKey(user, requestId);
        purgeExpired();
        cancelledRequests.put(key, Instant.now());
    }

    public void throwIfCancelled(User user, String requestId) {
        String key = keyOrNull(user, requestId);
        if (key == null) {
            return;
        }

        if (cancelledRequests.containsKey(key)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "COMPARE_CANCELLED",
                    "비교 검증이 중단되었습니다."
            );
        }
    }

    public void clear(User user, String requestId) {
        String key = keyOrNull(user, requestId);
        if (key != null) {
            cancelledRequests.remove(key);
        }
    }

    private String requireKey(User user, String requestId) {
        String key = keyOrNull(user, requestId);
        if (key == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_COMPARE_REQUEST_ID",
                    "비교 검증 요청 ID가 필요합니다."
            );
        }
        return key;
    }

    private String keyOrNull(User user, String requestId) {
        if (user == null || user.getUserId() == null || requestId == null || requestId.isBlank()) {
            return null;
        }
        return user.getUserId() + ":" + requestId.trim();
    }

    private void purgeExpired() {
        Instant expiresBefore = Instant.now().minus(CANCEL_TOKEN_TTL);
        cancelledRequests.entrySet().removeIf(entry -> entry.getValue().isBefore(expiresBefore));
    }
}
