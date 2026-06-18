package com.example.demo.service;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.SecurityAlertCode;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.IntegrityCheckItem;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.EvidenceRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrityVerificationService {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceManifestService evidenceManifestService;
    private final CustodyLogService custodyLogService;
    private final BlockchainAnchorRepository blockchainAnchorRepository;
    private final NotificationService notificationService;

    /**
     * RQ-SEC-153: 상세 조회 등에서 무결성 검증 후 실패 시 보안 알림 자동 발송.
     */
    @Transactional
    public IntegrityVerifyResponse verifyAndNotifySecurityIssues(User user, Long evidenceId) {
        Evidence evidence = requireOwnedEvidence(user, evidenceId);
        IntegrityVerifyResponse response = buildVerification(evidence);
        dispatchSecurityAlerts(user.getUserId(), evidenceId, response);
        return response;
    }

    /**
     * SK-632: 전용 검증 API — 실패 시 409 + errorCode.
     */
    @Transactional
    public IntegrityVerifyResponse verifyIntegrityOrThrow(User user, Long evidenceId) {
        IntegrityVerifyResponse response = verifyAndNotifySecurityIssues(user, evidenceId);
        if (!response.isValid()) {
            IntegrityCheckItem firstFailure = response.getChecks().stream()
                    .filter(check -> !check.isValid())
                    .findFirst()
                    .orElseThrow();
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    firstFailure.getErrorCode(),
                    firstFailure.getMessage()
            );
        }
        return response;
    }

    private IntegrityVerifyResponse buildVerification(Evidence evidence) {
        Long evidenceId = evidence.getEvidenceId();
        List<IntegrityCheckItem> checks = new ArrayList<>();
        checks.add(checkManifestSignature(evidenceId));
        checks.add(checkCustodyChain(evidenceId));
        checks.add(checkBlockchainHash(evidence));

        boolean valid = checks.stream().allMatch(IntegrityCheckItem::isValid);
        return IntegrityVerifyResponse.builder()
                .evidenceId(evidenceId)
                .valid(valid)
                .checks(checks)
                .build();
    }

    private IntegrityCheckItem checkManifestSignature(Long evidenceId) {
        return evidenceManifestService.findByEvidenceId(evidenceId)
                .map(this::evaluateManifestSignature)
                .orElse(IntegrityCheckItem.builder()
                        .checkType("SIGNATURE")
                        .valid(true)
                        .message("Manifest가 아직 생성되지 않았습니다.")
                        .build());
    }

    private IntegrityCheckItem evaluateManifestSignature(EvidenceManifest manifest) {
        SignatureStatus status = manifest.getSignatureStatus();
        if (status == SignatureStatus.FAILED) {
            return invalidSignature("Manifest 전자서명 생성에 실패했습니다.");
        }
        if (status == SignatureStatus.UNSIGNED) {
            return IntegrityCheckItem.builder()
                    .checkType("SIGNATURE")
                    .valid(true)
                    .message("서명 대기 상태입니다.")
                    .build();
        }
        if (!evidenceManifestService.isSignatureValid(manifest)) {
            return invalidSignature("Evidence Manifest X.509 서명 검증에 실패했습니다.");
        }
        return IntegrityCheckItem.builder()
                .checkType("SIGNATURE")
                .valid(true)
                .message("Manifest 서명이 유효합니다.")
                .build();
    }

    private IntegrityCheckItem invalidSignature(String message) {
        return IntegrityCheckItem.builder()
                .checkType("SIGNATURE")
                .valid(false)
                .errorCode(SecurityAlertCode.SIGNATURE_INVALID.name())
                .message(message)
                .build();
    }

    private IntegrityCheckItem checkCustodyChain(Long evidenceId) {
        boolean chainValid = custodyLogService.verifyChainIntegrity(
                CustodyTargetType.EVIDENCE, evidenceId);
        if (chainValid) {
            return IntegrityCheckItem.builder()
                    .checkType("COC_CHAIN")
                    .valid(true)
                    .message("CoC 해시 체인이 유효합니다.")
                    .build();
        }
        return IntegrityCheckItem.builder()
                .checkType("COC_CHAIN")
                .valid(false)
                .errorCode(SecurityAlertCode.CHAIN_INTEGRITY_FAILED.name())
                .message("증거 관리(CoC) 해시 체인 무결성 검증에 실패했습니다.")
                .build();
    }

    private IntegrityCheckItem checkBlockchainHash(Evidence evidence) {
        return blockchainAnchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                        evidence.getEvidenceId(),
                        BlockchainAnchorType.EVIDENCE_HASH
                )
                .filter(anchor -> anchor.getStatus() == BlockchainAnchorStatus.ANCHORED)
                .map(anchor -> evaluateBlockchainAnchor(evidence, anchor))
                .orElse(IntegrityCheckItem.builder()
                        .checkType("BLOCKCHAIN_HASH")
                        .valid(true)
                        .message("앵커링된 블록체인 기록이 없습니다.")
                        .build());
    }

    private IntegrityCheckItem evaluateBlockchainAnchor(Evidence evidence, BlockchainAnchor anchor) {
        String currentHash = evidence.getOriginalHashValue();
        if (currentHash != null && currentHash.equalsIgnoreCase(anchor.getSubjectHash())) {
            return IntegrityCheckItem.builder()
                    .checkType("BLOCKCHAIN_HASH")
                    .valid(true)
                    .message("블록체인 앵커 해시가 현재 원본 해시와 일치합니다.")
                    .build();
        }
        return IntegrityCheckItem.builder()
                .checkType("BLOCKCHAIN_HASH")
                .valid(false)
                .errorCode(SecurityAlertCode.BLOCKCHAIN_HASH_MISMATCH.name())
                .message("블록체인에 등록된 해시와 현재 증거 원본 해시가 일치하지 않습니다.")
                .build();
    }

    private void dispatchSecurityAlerts(Long userId, Long evidenceId, IntegrityVerifyResponse response) {
        for (IntegrityCheckItem check : response.getChecks()) {
            if (check.isValid() || check.getErrorCode() == null) {
                continue;
            }
            SecurityAlertCode alertCode = SecurityAlertCode.valueOf(check.getErrorCode());
            notificationService.notifySecurityAlertIfNeeded(userId, evidenceId, alertCode);
        }
    }

    private Evidence requireOwnedEvidence(User user, Long evidenceId) {
        return evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));
    }
}
