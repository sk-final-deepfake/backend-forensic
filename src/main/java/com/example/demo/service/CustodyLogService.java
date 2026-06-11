package com.example.demo.service;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
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
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustodyLogService {

    private static final String GENESIS_HASH = "GENESIS";

    private final CustodyLogRepository custodyLogRepository;

    @Transactional
    public void recordUserAction(User actor, User target, String actionType, String reason) {
        String previousHash = custodyLogRepository.findTopByOrderByLogIdDesc()
                .map(CustodyLog::getCurrentLogHash)
                .orElse(GENESIS_HASH);

        LocalDateTime now = LocalDateTime.now();
        String payload = actor.getUserId() + "|" + target.getUserId() + "|" + actionType + "|" + now;
        String currentHash = sha256Hex(payload + previousHash);

        CustodyLog log = new CustodyLog();
        log.setActorId(actor.getUserId());
        log.setTargetType(CustodyTargetType.USER);
        log.setTargetId(target.getUserId());
        log.setActionType(actionType);
        log.setReason(reason);
        log.setPreviousLogHash(GENESIS_HASH.equals(previousHash) ? null : previousHash);
        log.setCurrentLogHash(currentHash);
        log.setCreatedAt(now);
        custodyLogRepository.save(log);
    }

    @Transactional
    public void recordEvidenceAction(User actor, Evidence evidence, String actionType, String reason) {
        String previousHash = custodyLogRepository.findTopByOrderByLogIdDesc()
                .map(CustodyLog::getCurrentLogHash)
                .orElse(GENESIS_HASH);

        LocalDateTime now = LocalDateTime.now();
        String payload = actor.getUserId() + "|" + evidence.getEvidenceId() + "|" + actionType + "|" + now;
        String currentHash = sha256Hex(payload + previousHash);

        CustodyLog log = new CustodyLog();
        log.setActorId(actor.getUserId());
        log.setTargetType(CustodyTargetType.EVIDENCE);
        log.setTargetId(evidence.getEvidenceId());
        log.setActionType(actionType);
        log.setSubjectHash(evidence.getOriginalHashValue());
        log.setReason(reason);
        log.setPreviousLogHash(GENESIS_HASH.equals(previousHash) ? null : previousHash);
        log.setCurrentLogHash(currentHash);
        log.setCreatedAt(now);
        custodyLogRepository.save(log);
    }

    public boolean verifyChainIntegrity(CustodyTargetType targetType, Long targetId) {
        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId);
        if (logs.isEmpty()) {
            return true;
        }

        String expectedPreviousHash = null;
        for (CustodyLog log : logs) {
            if (!Objects.equals(log.getPreviousLogHash(), expectedPreviousHash)) {
                return false;
            }
            expectedPreviousHash = log.getCurrentLogHash();
        }
        return true;
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
