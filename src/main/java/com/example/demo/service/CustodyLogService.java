package com.example.demo.service;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.repository.CustodyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class CustodyLogService {

    private final CustodyLogRepository custodyLogRepository;

    @Transactional
    public void recordUserAction(User actor, User target, String actionType, String reason) {
        record(
                actor.getUserId(),
                CustodyTargetType.USER,
                target.getUserId(),
                actionType,
                null,
                null,
                reason,
                null,
                null
        );
    }

    @Transactional
    public CustodyLog record(
            Long actorId,
            CustodyTargetType targetType,
            Long targetId,
            String actionType,
            String subjectHash,
            String storagePathAtEvent,
            String reason,
            String eventPayloadJson,
            String clientIp
    ) {
        validateRequired(actorId, targetType, targetId, actionType);

        String previousLogHash = custodyLogRepository.findTopByOrderByLogIdDesc()
                .map(CustodyLog::getCurrentLogHash)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();

        CustodyLog log = new CustodyLog();
        log.setActorId(actorId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setActionType(actionType);
        log.setSubjectHash(subjectHash);
        log.setStoragePathAtEvent(storagePathAtEvent);
        log.setReason(reason);
        log.setClientIp(clientIp);
        log.setEventPayloadJson(eventPayloadJson);
        log.setPreviousLogHash(previousLogHash);
        log.setCreatedAt(now);

        log.setCurrentLogHash(sha256Hex(buildHashInput(log)));
        return custodyLogRepository.save(log);
    }

    private void validateRequired(
            Long actorId,
            CustodyTargetType targetType,
            Long targetId,
            String actionType
    ) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType is required");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("targetId is required");
        }
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("actionType is required");
        }
    }

    private String buildHashInput(CustodyLog log) {
        return String.join("|",
                valueOf(log.getPreviousLogHash()),
                valueOf(log.getActorId()),
                valueOf(log.getTargetType()),
                valueOf(log.getTargetId()),
                valueOf(log.getActionType()),
                valueOf(log.getSubjectHash()),
                valueOf(log.getStoragePathAtEvent()),
                valueOf(log.getReason()),
                valueOf(log.getEventPayloadJson()),
                valueOf(log.getClientIp()),
                valueOf(log.getCreatedAt())
        );
    }

    private String valueOf(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
